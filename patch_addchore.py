import os, shutil, sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
path = None
for d, dirs, files in os.walk(root):
    dirs[:] = [x for x in dirs if x not in {"build", ".git", ".gradle", ".idea"}]
    if "AddChoreViewModel.kt" in files:
        path = os.path.join(d, "AddChoreViewModel.kt")
        break
if not path:
    sys.exit("AddChoreViewModel.kt not found")

NEW = {
    "// Reads membership, history and workload fresh": [
        "// Decides who gets an automatically assigned chore.",
        "// Loads members, completions and open chores ONCE from Firebase (fresh, never from an earlier result),",
        "// then works out the whole priority order locally. Nothing is fetched per roommate.",
        "// A repeating chore gets the complete priority order, which is saved on it and never recalculated.",
        "// A one-time chore only gets its highest-priority roommate, with no order.",
    ],
    "// One seed per form": [
        "// One random seed per form. The preview and the save both shuffle with it, so when two roommates are",
        "// completely tied they are put in the same order in both, as long as nothing else has changed.",
    ],
}

lines = open(path, encoding="utf-8").read().split("\n")
out, i = [], 0
shutil.copy(path, path + ".bak")
while i < len(lines):
    stripped = lines[i].strip()
    key = next((k for k in NEW if stripped.startswith(k)), None)
    if key:
        indent = lines[i][: len(lines[i]) - len(lines[i].lstrip())]
        while i < len(lines) and lines[i].strip().startswith("//"):
            i += 1
        out += [indent + l for l in NEW[key]]
        print("patched:", key)
    else:
        out.append(lines[i])
        i += 1
open(path, "w", encoding="utf-8").write("\n".join(out))
