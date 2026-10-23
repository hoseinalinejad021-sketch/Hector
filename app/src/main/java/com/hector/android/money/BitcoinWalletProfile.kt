package com.hector.android.money

/**
 * Receive-only destination profile. It deliberately stores no seed phrase or private key
 * and cannot sign, broadcast, or initiate a transaction.
 */
data class BitcoinWalletProfile(
    val address: String,
    val network: BitcoinNetwork = BitcoinNetwork.MAINNET
) {
    init {
        require(address.isNotBlank()) { "Bitcoin receiving address is required" }
        require(address.none { it.isWhitespace() }) { "Address must not contain whitespace" }
        require(address.length in 26..90) { "Address length is outside the expected range" }
        when (network) {
            BitcoinNetwork.MAINNET -> require(
                address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1")
            ) { "Address prefix does not look like a Bitcoin mainnet address" }
            BitcoinNetwork.TESTNET -> require(
                address.startsWith("m") || address.startsWith("n") ||
                    address.startsWith("2") || address.startsWith("tb1")
            ) { "Address prefix does not look like a Bitcoin testnet address" }
        }
    }
}

enum class BitcoinNetwork {
    MAINNET,
    TESTNET
}
