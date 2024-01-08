package ftt.sql

data class PlayerClaimData(
    val name: String?,
    val uuid: String?,
    val claim: String,
    val cost: Int,
    val daysPerRent: Int,
    val endTime: Long?,
    val renew: Boolean?
)