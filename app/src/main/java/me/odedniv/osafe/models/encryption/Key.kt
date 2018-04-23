package me.odedniv.osafe.models.encryption

class Key(val label: Label, val content: Content) {
    enum class Label {
        PASSPHRASE
    }
}