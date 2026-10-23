package com.hector.android.money

/**
 * Public receiving address supplied by the project owner.
 *
 * This is an EVM-format address; it does not identify a specific chain.
 * Balance lookups must remain disabled until a chain is explicitly selected.
 * No private key, seed phrase, signing, or transaction sending is supported here.
 */
object ReceivingWalletConfig {
    const val ADDRESS: String = "0x204Fb0eD6b406f4A8462E31f6f41735F3c18917F"
    const val ADDRESS_TYPE: String = "EVM"
    const val NETWORK_CONFIRMED: Boolean = false
    const val RECEIVE_ONLY: Boolean = true
    const val PRIVATE_KEY_STORED: Boolean = false
    const val OUTGOING_TRANSACTIONS_ENABLED: Boolean = false
}
