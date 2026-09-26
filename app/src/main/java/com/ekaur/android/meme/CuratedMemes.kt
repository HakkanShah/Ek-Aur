package com.ekaur.android.meme

/**
 * The GIFs the reminder popup may show, picked by hand from GIPHY: on-topic
 * (touch grass, go to bed, get off your phone, that's enough internet, go
 * outside, take a break, log off) and nothing else. A live search wandered
 * into unrelated GIFs; a list doesn't.
 *
 * This is the built-in copy. The app also asks the website for the current
 * list ([REMOTE_URL]), so GIFs can be added or dropped without an update; if
 * that can't be reached, this is what it uses.
 */
object CuratedMemes {

    const val REMOTE_URL = "https://ek-aur.vercel.app/api/memes"

    val BUILT_IN: List<String> = listOf(
        "q5jnZ0d18LEtOgAICr",
        "VZ2HZ7PaaYnN7Ajdy3",
        "T7xwGxSMc1oUfvSRNh",
        "MYxGTb8PTZWkLUesqb",
        "o0jdtX0f8Fe0keeCYj",
        "AMnQxpY5EsVakBHTY4",
        "gYRJAOR9b5gl1Sscl1",
        "chUlbSh9bzXhw7fjyL",
        "kZoEhRL0Z1puX8LKaF",
        "rvxGjhW3TKVeo",
        "x8lRvcyGCU0Zo0pn0E",
        "UIKUeFj40vTP2",
        "xT1Ra163yfoMJjSVC8",
        "iJAzrPmzm3mUruTNcl",
        "gLWaccKeVwJaqNDfxL",
        "3o6nV7lzpqgPJC0XPW",
        "A1PP9Cud9Q1uJzaFPq",
        "a0uAI2vaVf6VGwoZaL",
        "2Y7r7yioGYD4qohxqG",
        "f94ljPCw8BqXnbTHz0",
        "26F3ZmkUVCjfBIEmY",
        "GbLzzhgFa3RJNsZhBj",
        "ii4xq61GYnWDmxBBBE",
        "bF26tUvs3TxMAAqEtk",
        "6lG0GvjMRrJBZvl4Q5",
        "xkYuNjqV3wfzW",
        "2Gt1DcNcKJeLPKPsyB",
        "aNC4J6brC2HWn6Owg7",
        "1TJP5EVbLGQX5Kedy6",
        "9BGebtqLr9L93Ye7Zm",
        "YD6rMlVky8yevuDBGS",
        "3oEhmGtqMcvubRFl4c",
        "BxZstOJqJHt6XU8oSI",
        "QgcQLZa6glP2w",
        "ezhq0HYCGFp6yMrpYF",
        "BriX6AvuHRWtSGopcP",
        "7G1OtZwhRsr6ciuRIU",
        "aKAl59R0WFIQFdvNkw",
        "LyGIzEiH0W9nIdu2yh",
        "xT1R9UBu3a9Qhm2aha",
        "sCpz7CTSlWvxN3GgPY",
        "igsmXEkeyfPPgfU2yI",
        "pj607lp47yc9UVGNbO",
        "5C0b1FIpKtd8h8YwMa",
        "ijY2AXUcTCn3IFOpAU",
        "BxARA9L49ZhBFcw3jh",
        "DHffwPMJIgKAc0v6Av",
        "nVB50QR6DqSYIbwWJB",
        "fwph1nbARjAFPhLAwU",
        "zNz0zWnX6WzdAAreiE",
    )

    /** A GIPHY id: letters and digits only, so a bad list can never build a strange URL. */
    fun isValidId(id: String): Boolean = id.length in 6..40 && id.all { it.isLetterOrDigit() }
}
