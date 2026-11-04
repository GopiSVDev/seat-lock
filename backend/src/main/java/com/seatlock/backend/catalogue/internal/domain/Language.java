package com.seatlock.backend.catalogue.internal.domain;

// Language a show is played in. Kept in sync with the ck_shows_language check constraint
// (a new value needs a migration too).
public enum Language {
    ENGLISH, HINDI, TAMIL, TELUGU, MALAYALAM, KANNADA, BENGALI, MARATHI,
    GUJARATI, PUNJABI, ODIA, ASSAMESE, BHOJPURI, URDU,
    JAPANESE, KOREAN, FRENCH, SPANISH
}
