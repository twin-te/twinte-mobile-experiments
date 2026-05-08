package net.twinte.mobile_experiments

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform