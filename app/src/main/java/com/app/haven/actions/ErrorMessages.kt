package com.app.haven.actions

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun Throwable.friendlyMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
    is FirebaseAuthUserCollisionException -> "An account with that email already exists"
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException -> "Incorrect email or password"
    is FirebaseNetworkException -> "No connection. Check your internet and try again"
    else -> message ?: "Something went wrong"
}