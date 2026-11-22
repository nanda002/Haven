/**
 * Haven's Cloud Functions: server code that runs on Google's servers, not on
 * the phones. The app never calls these directly; they react to changes in
 * Firestore
 *
 * notifyOnNewExpense - when an expense is added, push a notification to the
 * other members in its split.
 * notifyOnUpdateExpense - when an expense's description, amount, payer or split
 * changes, notify everyone who was or is in the split (except the payer).
 * notifyOnNewSettlement - when a payment is recorded, notify the other person.
 */

const {setGlobalOptions} = require("firebase-functions");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

// server access to the project
initializeApp();

setGlobalOptions({region: "us-central1", maxInstances: 10});

const DEAD_TOKEN_ERRORS = [
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
];

const money = (cents) => `$${(cents / 100).toFixed(2)}`;

async function sendExpenseNotifications({
  householdId,
  expenseId,
  expense,
  recipientUids,
  action,
}) {
  if (recipientUids.length === 0) return;

  const db = getFirestore();

  // Retrieve recipient documents
  const userDocs = await db.getAll(
      ...recipientUids.map((uid) => db.doc(`users/${uid}`)),
  );

  const shares = expense.shares || {};
  const total = money(expense.amountCents);
  const messages = [];

  // Build one message per device
  for (const userDoc of userDocs) {
    // Getting the user token for each user
    const tokens = userDoc.get("fcmTokens") || [];
    // Checking if user is part of shares (required for editing expense)
    const hasShare = Object.prototype.hasOwnProperty.call(
        shares,
        userDoc.id,
    );
    // Creating the body of the notification
    const body = hasShare ?
      `Your share: ${money(shares[userDoc.id])} of ${total}` :
      `Your share in this expense was removed.`;

    for (const token of tokens) {
      messages.push({
        uid: userDoc.id,
        token,
        payload: {
          token,
          notification: {
            title: `${expense.paidByName} ${action} ${expense.description}`,
            body,
          },
          android: {
            priority: "high",
            notification: {channelId: "expenses"},
          },
          data: {
            type: "expense",
            householdId,
          },
        },
      });
    }
  }

  await sendAndCleanUp(messages, userDocs, `expense ${expenseId} ${action}`);
}


/**
 * Sends the messages, logs how many arrived, and removes tokens of phones
 * that can't receive anymore (app uninstalled, data cleared).
 * @param {Object[]} messages {uid, token, payload} for each phone
 * @param {Object[]} userDocs the recipients' users/{uid} documents
 * @param {string} label what was sent, for the logs
 */
async function sendAndCleanUp(messages, userDocs, label) {
  if (messages.length === 0) return;

  // Send notifications
  const result = await getMessaging().sendEach(
      messages.map((message) => message.payload),
  );

  logger.info(`${label}: sent ${result.successCount}/${messages.length}`);

  // Identify invalid tokens, grouped by user
  const deadTokensByUser = new Map();

  result.responses.forEach((response, i) => {
    if (
      response.error &&
      DEAD_TOKEN_ERRORS.includes(response.error.code)
    ) {
      const uid = messages[i].uid;

      if (!deadTokensByUser.has(uid)) {
        deadTokensByUser.set(uid, []);
      }

      deadTokensByUser.get(uid).push(messages[i].token);
    }
  });

  // Remove invalid tokens from Firestore
  await Promise.all(
      userDocs.map((userDoc) => {
        const dead = deadTokensByUser.get(userDoc.id) || [];

        if (dead.length === 0) return null;

        return userDoc.ref.update({
          fcmTokens: FieldValue.arrayRemove(...dead),
        });
      }),
  );
}


// 1. New expense
exports.notifyOnNewExpense = onDocumentCreated(
    "households/{householdId}/expenses/{expenseId}",
    async (event) => {
    // event.data is the wrapper around the doc
    // while event.data.data() is the field and its values
    // null check
      const expense = event.data && event.data.data();
      if (!expense) return;

      const shares = expense.shares || {};

      const recipients = Object.keys(shares).filter(
          (uid) => uid !== expense.paidByUid,
      );

      await sendExpenseNotifications({
        householdId: event.params.householdId,
        expenseId: event.params.expenseId,
        expense,
        recipientUids: recipients,
        action: "added",
      });
    },
);


// 2. Updated expense
exports.notifyOnUpdateExpense = onDocumentUpdated(
    "households/{householdId}/expenses/{expenseId}",
    async (event) => {
      const before = event.data.before.data();
      const after = event.data.after.data();

      // Compare expense fields (a new payer changes everyone's balances)
      const expenseChanged =
        before.description !== after.description ||
        before.amountCents !== after.amountCents ||
        before.paidByUid !== after.paidByUid;

      // Compare shares independently of object key ordering
      const oldShares = before.shares || {};
      const newShares = after.shares || {};

      const allUids = [
        ...new Set([
          ...Object.keys(oldShares),
          ...Object.keys(newShares),
        ]),
      ];

      const sharesChanged = allUids.some(
          (uid) => oldShares[uid] !== newShares[uid],
      );

      if (!expenseChanged && !sharesChanged) return;

      // Include old and new participants so removed members
      // are also informed about the change
      const recipients = allUids.filter(
          (uid) => uid !== after.paidByUid,
      );

      await sendExpenseNotifications({
        householdId: event.params.householdId,
        expenseId: event.params.expenseId,
        expense: after,
        recipientUids: recipients,
        action: "updated",
      });
    },
);


// 3. New settlement
exports.notifyOnNewSettlement = onDocumentCreated(
    "households/{householdId}/settlements/{settlementId}",
    async (event) => {
      const settlement = event.data && event.data.data();
      if (!settlement) return;

      const {fromUid, toUid, amount, whoSettled, settledInFull} = settlement;
      if (!fromUid || !toUid) return;

      const recipientUid = whoSettled === toUid ? fromUid : toUid;
      if (recipientUid === whoSettled) return;

      const householdId = event.params.householdId;
      const db = getFirestore();
      const [userDoc, recorderDoc] = await db.getAll(
          db.doc(`users/${recipientUid}`),
          db.doc(`households/${householdId}/members/${whoSettled}`),
      );

      const name = recorderDoc.get("displayName") || "A roommate";
      const paid = money(amount);
      const allSettled = settledInFull ? " You're all settled up." : "";

      const recipientGotPaid = recipientUid === toUid;
      const title = recipientGotPaid ?
        `${name} paid you ${paid}` :
        `${name} received your ${paid} payment`;
      const body = recipientGotPaid ?
        `Payment recorded in Haven.${allSettled}` :
        `${name} marked it as received.${allSettled}`;

      const tokens = userDoc.get("fcmTokens") || [];
      const messages = tokens.map((token) => ({
        uid: recipientUid,
        token,
        payload: {
          token,
          notification: {title, body},
          android: {
            priority: "high",
            notification: {channelId: "expenses"},
          },
          data: {type: "expense", householdId},
        },
      }));

      await sendAndCleanUp(
          messages,
          [userDoc],
          `settlement ${event.params.settlementId}`,
      );
    },
);
