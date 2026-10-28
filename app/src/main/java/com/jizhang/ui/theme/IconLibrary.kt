package com.jizhang.ui.theme

/**
 * Extra line icons for custom categories, in the same 24×24, 1.9-stroke, round-cap style as
 * the prototype glyphs in [LedgerIcons]. Keys are stored in the database: never rename one.
 */
object IconLibrary {
    data class Group(val title: String, val icons: List<String>)

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = LedgerIcons.rect(x, y, w, h, r)
    private fun ring(cx: Float, cy: Float, r: Float) = LedgerIcons.ring(cx, cy, r)

    /** Path data per icon key (SVG path syntax; rects/circles via LedgerIcons.rect / ring). */
    val paths: Map<String, List<String>> = mapOf(
        // 餐饮
        "coffee" to listOf("M5 9h11v4.5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5V9Z", "M16 10h1.5a2.5 2.5 0 0 1 0 5H16", "M8.5 3.5v2.5M12.5 3.5v2.5", "M4.5 21h12"),
        "milk-tea" to listOf("M5.5 8h13", "M7 8l1.2 12.5h7.6L17 8", "M12.5 8l1.5-5h3", "M10 17h.01M12 15.5h.01M14 17h.01"),
        "noodles" to listOf("M4 12h16a8 8 0 0 1-16 0Z", "M8.5 9c-.8-1 .8-2 0-3.5M12 9c-.8-1 .8-2 0-3.5M15.5 9c-.8-1 .8-2 0-3.5"),
        "burger" to listOf("M4.5 10.5a7.5 6 0 0 1 15 0Z", "M4 14h16", "M5 17.5h14v.5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-.5Z", "M10 7.5h.01M14 7.5h.01"),
        "pizza" to listOf("M3.5 7c5.5-3.3 11.5-3.3 17 0L12 21Z", "M5.3 10c4.3-2.3 9.1-2.3 13.4 0", "M10 12.5h.01M14 13h.01M12 16.5h.01"),
        "cake" to listOf("M4 20.5h16", "M5.5 20.5v-6a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v6", "M5.5 16c1.1 0 1.6 1 3.25 1s2.15-1 3.25-1 1.6 1 3.25 1 2.15-1 3.25-1", "M12 12.5V9", "M12 3.5c1 1 1.3 2.3 0 3-1.3-.7-1-2 0-3Z"),
        "fruit" to listOf(
            "M12 8.5c-1.5-1-3-1.5-4.5-1.5C5 7 4 9.5 4 12c0 4 3 8.5 5.5 8.5 1.2 0 1.5-.6 2.5-.6s1.3.6 2.5.6c2.5 0 5.5-4.5 5.5-8.5 0-2.5-1-5-3.5-5-1.5 0-3 .5-4.5 1.5Z",
            "M12 8.5c0-2.5 1-4.5 3.5-5",
        ),
        "vegetable" to listOf("M4 20c3.5-1 8.5-4 10.8-6.6a3 3 0 0 0-4.2-4.2C8 11.5 5 16.5 4 20Z", "M15 9l3.5-3.5M14 7.8l.8-4M16.2 10l4-.8"),
        "snack" to listOf(ring(12f, 12f, 4f), "M8.3 10.3L4.5 8v8l3.8-2.3", "M15.7 10.3L19.5 8v8l-3.8-2.3"),
        "wine" to listOf("M7.5 3h9l.3 4.5a4.8 4.8 0 0 1-9.6 0Z", "M7.4 7h9.2", "M12 12.3v8.2M8.5 20.5h7"),
        "beer" to listOf(
            "M5.5 7.5h9.5v11a2 2 0 0 1-2 2H7.5a2 2 0 0 1-2-2Z", "M15 10h2a2 2 0 0 1 2 2v2.5a2 2 0 0 1-2 2h-2",
            "M5 7.5a2.5 2.5 0 0 1 3.5-3 3 3 0 0 1 5 .2 2.3 2.3 0 0 1 2 2.8", "M9 11v6M12 11v6",
        ),

        // 出行
        "car" to listOf(
            "M5 17H3.5v-4.5L5.5 8a1.5 1.5 0 0 1 1.4-1h10.2a1.5 1.5 0 0 1 1.4 1l2 4.5V17H19", "M3.5 12.5h17",
            ring(7f, 17f, 2f), ring(17f, 17f, 2f), "M9 17h6",
        ),
        "taxi" to listOf(
            "M6 11.5l1.4-4.2a1 1 0 0 1 1-.8h7.2a1 1 0 0 1 1 .8l1.4 4.2", rect(4f, 11.5f, 16f, 5.5f, 2f),
            "M6.5 17v2.5M17.5 17v2.5", "M7.5 14.2h.01M16.5 14.2h.01", "M10.5 6.5V4h3v2.5",
        ),
        "bus" to listOf(
            "M3.5 16V7a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v9h-1.5a2.5 2.5 0 0 0-5 0H10a2.5 2.5 0 0 0-5 0Z",
            "M3.5 10.5h17", "M9 5v5.5M15 5v5.5", ring(7.5f, 17f, 1.6f), ring(16.5f, 17f, 1.6f),
        ),
        "train" to listOf(rect(6f, 3f, 12f, 14f, 3.5f), rect(8.5f, 6f, 7f, 4f, 1f), "M9 13.5h.01M15 13.5h.01", "M8.5 20.5l1.5-3.5M15.5 20.5L14 17"),
        "bike" to listOf(ring(6f, 16f, 3.5f), ring(18f, 16f, 3.5f), "M6 16l3.5-7h6L18 16M6 16h6l3.5-7M12 16L9.5 9", "M9.5 9L9 6.8M7.5 6.8h3M15.5 9l-1-2.5h2.5"),
        "plane" to listOf("M12 3c1 0 1.5 1 1.5 2.5v4l7 4v2l-7-2.2v4.2l2.5 1.8V21l-4-1-4 1v-1.7l2.5-1.8v-4.2l-7 2.2v-2l7-4v-4C10.5 4 11 3 12 3Z"),
        "ship" to listOf("M3.5 15h17l-2 5H5.5Z", "M6.5 15v-4h11v4", "M10 11V7h4v4", "M12 7V4"),
        "fuel" to listOf(
            "M5 20.5V5a1.5 1.5 0 0 1 1.5-1.5h6A1.5 1.5 0 0 1 14 5v15.5", "M3.5 20.5h12", "M5 10h9",
            "M14 8.5h1.5a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 0 3 0V8.5L17.5 6",
        ),
        "parking" to listOf(rect(4f, 4f, 16f, 16f, 4f), "M10 16.5v-9h2.75a2.5 2.5 0 0 1 0 5H10"),

        // 购物
        "shirt" to listOf("M8.5 4L3.5 7l2 4 2.5-1.2V20h8V9.8l2.5 1.2 2-4-5-3a3.5 3.5 0 0 1-7 0Z"),
        "shoes" to listOf(
            "M4 16.5v-10l4 .5 1.5 3c2.5 1.8 6.5 2.3 9 3.7a2.5 2.5 0 0 1 1.5 2.3v.5",
            "M3.5 16.5h17V18a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 18Z", "M10.5 12l1.5-1.5M13 13l1.5-1.5",
        ),
        "handbag" to listOf(rect(4f, 11f, 16f, 9f, 2.5f), "M6.5 11L9 4.5h6l2.5 6.5", "M4 14.5h16", "M12 14.5v2"),
        "lipstick" to listOf(rect(7.5f, 13f, 9f, 7.5f, 1.5f), "M9 13V9.5h6V13", "M10 9.5v-3L14 4v5.5"),
        "diamond" to listOf("M7 4h10l3.5 5L12 20.5 3.5 9Z", "M3.5 9h17", "M9.5 4L8 9l4 11.5L16 9l-1.5-5"),
        "glasses" to listOf(ring(6.5f, 14.5f, 3.5f), ring(17.5f, 14.5f, 3.5f), "M10 14a2 2 0 0 1 4 0", "M3.2 13.5L5 7.5M20.8 13.5L19 7.5"),
        "watch" to listOf(ring(12f, 12f, 5.5f), "M12 9.5V12l1.5 1.5", "M9 7.3l.7-3.8h4.6l.7 3.8M9 16.7l.7 3.8h4.6l.7-3.8"),
        "cart" to listOf("M3 4h2.5l2 10.5h10.5l2-7H6.3", ring(9f, 18.5f, 1.5f), ring(17f, 18.5f, 1.5f)),
        "box" to listOf("M4 7.5l8-4 8 4v9l-8 4-8-4Z", "M4 7.5l8 4 8-4", "M12 11.5v9", "M8 5.5l8 4"),

        // 居家
        "key" to listOf(ring(8f, 16f, 4f), "M10.8 13.2l8.7-8.7", "M16.5 7.5L19 10M14.5 9.5l2 2"),
        "building" to listOf("M5 20.5V5a1.5 1.5 0 0 1 1.5-1.5h7A1.5 1.5 0 0 1 15 5v15.5", "M15 10h3.5a1.5 1.5 0 0 1 1.5 1.5v9", "M3.5 20.5h17", "M8.5 7.5h3M8.5 11h3M8.5 14.5h3"),
        "bolt" to listOf("M13.5 3L5 13.5h6.5L10.5 21 19 10.5h-6.5Z"),
        "drop" to listOf("M12 3.5C9.5 6.5 6 10.5 6 14.5a6 6 0 0 0 12 0c0-4-3.5-8-6-11Z", "M9.3 15a2.7 2.7 0 0 0 2.7 2.7"),
        "flame" to listOf(
            "M12 21a6 6 0 0 0 6-6c0-4.5-3.5-6.5-5-11.5-2.5 2.5-3.5 5-3.5 7.5-1.2-.5-2-1.6-2.5-3C5.8 9.5 6 12 6 15a6 6 0 0 0 6 6Z",
            "M12 21c-1.5 0-2.5-1.2-2.5-2.5 0-1.5 1.3-2.5 2.5-4 1.2 1.5 2.5 2.5 2.5 4 0 1.3-1 2.5-2.5 2.5Z",
        ),
        "wifi" to listOf("M3.5 9.5a12 12 0 0 1 17 0", "M6.5 12.8a7.8 7.8 0 0 1 11 0", "M9.5 16.1a3.5 3.5 0 0 1 5 0", "M12 19.5h.01"),
        "sofa" to listOf(
            "M5.5 11V8A2.5 2.5 0 0 1 8 5.5h8A2.5 2.5 0 0 1 18.5 8v3",
            "M3.5 13a1.75 1.75 0 0 1 3.5 0v1.5h10V13a1.75 1.75 0 0 1 3.5 0v5.5h-17Z", "M5.5 18.5v2M18.5 18.5v2",
        ),
        "laundry" to listOf(rect(4.5f, 3f, 15f, 18f, 2.5f), "M4.5 7.5h15", ring(12f, 14f, 4f), "M7.5 5.3h.01M10.5 5.3h.01"),
        "wrench" to listOf("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.3-3.3a5.5 5.5 0 0 1-7.4 7.4l-6.9 6.9a2.13 2.13 0 0 1-3-3l6.9-6.9a5.5 5.5 0 0 1 7.4-7.4Z"),
        "cigarette" to listOf(rect(3.5f, 13f, 17f, 4f, 1f), "M15.5 13v4", "M15.5 10c0-1.5 1.5-1.5 1.5-3s-1.5-1.5-1.5-3M19 10c0-1.5 1.5-1.5 1.5-3s-1.5-1.5-1.5-3"),

        // 数码办公
        "laptop" to listOf(rect(5f, 5f, 14f, 10f, 1.5f), "M3 18.5h18"),
        "phone" to listOf(rect(6.5f, 3f, 11f, 18f, 2.5f), "M10.5 17.5h3"),
        "monitor" to listOf(rect(3f, 4f, 18f, 12.5f, 2f), "M8.5 20.5h7M12 16.5v4"),
        "headphones" to listOf("M4.5 14.5V13a7.5 7.5 0 0 1 15 0v1.5", rect(3.5f, 13.5f, 4f, 6.5f, 1.5f), rect(16.5f, 13.5f, 4f, 6.5f, 1.5f)),
        "gamepad" to listOf(
            "M7.5 6.5h9a4.5 4.5 0 0 1 4.4 5.4l-.9 4.4a2.3 2.3 0 0 1-3.9 1.1L14.5 15.5h-5l-1.6 1.9A2.3 2.3 0 0 1 4 16.3l-.9-4.4A4.5 4.5 0 0 1 7.5 6.5Z",
            "M7.5 9.5v3.5M5.75 11.25h3.5", "M15.5 10.5h.01M17.5 12.5h.01",
        ),
        "camera" to listOf(
            "M3.5 8.5A1.5 1.5 0 0 1 5 7h2.8l1.5-2.5h5.4L16.2 7H19a1.5 1.5 0 0 1 1.5 1.5v10A1.5 1.5 0 0 1 19 20H5a1.5 1.5 0 0 1-1.5-1.5Z",
            ring(12f, 13f, 3.5f),
        ),
        "subscribe" to listOf("M19.5 12a7.5 7.5 0 0 1-13.2 4.9", "M4.5 12a7.5 7.5 0 0 1 13.2-4.9", "M18 3.5v3.6h-3.6", "M6 20.5v-3.6h3.6"),
        "pen" to listOf("M12 21l-5-9.5 2-8h6l2 8Z", "M12 20.5V13", ring(12f, 11.5f, 1.5f)),
        "gradcap" to listOf("M3 9.5L12 5l9 4.5L12 14Z", "M6.5 11.5v4.3c3.5 2.6 7.5 2.6 11 0v-4.3", "M21 9.5v5"),
        "briefcase" to listOf(rect(3.5f, 7f, 17f, 12.5f, 2.5f), "M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7", "M3.5 12.5h17"),
        "printer" to listOf(
            "M7 9V3.5h10V9",
            "M7 17H5.5A1.5 1.5 0 0 1 4 15.5v-5A1.5 1.5 0 0 1 5.5 9h13a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5H17",
            "M7 14h10v6.5H7Z",
        ),

        // 健康
        "pill" to listOf("M4.6 12.4l7.8-7.8a4.25 4.25 0 0 1 6 6l-7.8 7.8a4.25 4.25 0 0 1-6-6Z", "M8.5 8.5l6 6"),
        "hospital" to listOf("M5 20.5v-14A1.5 1.5 0 0 1 6.5 5h11A1.5 1.5 0 0 1 19 6.5v14", "M3.5 20.5h17", "M12 8v5M9.5 10.5h5", "M10 20.5V17h4v3.5"),
        "pulse" to listOf("M3 12h3.5L9 6l4 12 2.5-6H21"),
        "syringe" to listOf("M14 6l4 4-8 8H6v-4Z", "M16 8l3-3M17.5 3.5l3 3", "M6 18l-2.5 2.5", "M11 9l1.5 1.5M8.5 11.5L10 13"),
        "tooth" to listOf(
            "M12 6c-1.5-1.2-3-2-4.5-2C5 4 4 6 4 8.5c0 2.5 1 4 1.5 6l1 5a1 1 0 0 0 1.9.2L10 15.5a2 2 0 0 1 4 0l1.6 4.2a1 1 0 0 0 1.9-.2l1-5c.5-2 1.5-3.5 1.5-6C20 6 19 4 16.5 4 15 4 13.5 4.8 12 6Z",
        ),
        "dumbbell" to listOf(rect(5f, 6f, 3f, 12f, 1f), rect(16f, 6f, 3f, 12f, 1f), "M3.5 9.5v5M20.5 9.5v5M8 12h8"),
        "scissors" to listOf(ring(6.5f, 17f, 2.5f), ring(17.5f, 17f, 2.5f), "M8.2 15.1L18 4M15.8 15.1L6 4"),
        "spa" to listOf(
            "M12 19.5c-2-1.8-3-4.3-3-6.8s1.2-4.7 3-6.5c1.8 1.8 3 4 3 6.5s-1 5-3 6.8Z",
            "M12 19.5c-4 0-7.5-2.5-8.3-6.8 2.3-.1 4.3.6 5.6 1.8", "M12 19.5c4 0 7.5-2.5 8.3-6.8-2.3-.1-4.3.6-5.6 1.8",
        ),

        // 人情
        "hongbao" to listOf(rect(5f, 3f, 14f, 18f, 2f), "M5 7.5l7 4 7-4", "M9.5 13.5l2.5 2.8 2.5-2.8M12 16.3v3.2M10 17.5h4"),
        "heart" to listOf("M12 20s-8-4.5-8-10.5A4.5 4.5 0 0 1 12 6.6a4.5 4.5 0 0 1 8 2.9C20 15.5 12 20 12 20Z"),
        "wedding" to listOf(ring(12f, 15f, 5.5f), "M9.5 7l1-2.5h3l1 2.5L12 9.5Z"),
        "baby" to listOf(ring(12f, 13f, 7.5f), "M12 5.5c0-1.4 1-2.2 2.3-2", "M9.5 12.5h.01M14.5 12.5h.01", "M10 16a2.5 2.5 0 0 0 4 0"),
        "paw" to listOf(
            "M12 12c-3 0-5.5 3.5-5.5 5.5 0 1.5 1 2.5 2.5 2.5 1.2 0 2-.6 3-.6s1.8.6 3 .6c1.5 0 2.5-1 2.5-2.5 0-2-2.5-5.5-5.5-5.5Z",
            ring(5.5f, 10.5f, 1.7f), ring(9.3f, 6f, 1.7f), ring(14.7f, 6f, 1.7f), ring(18.5f, 10.5f, 1.7f),
        ),
        "friends" to listOf(ring(9f, 8f, 3.5f), "M3 20a6 6 0 0 1 12 0", "M15.5 4.6a3.5 3.5 0 0 1 0 6.8", "M17.5 14.4a6 6 0 0 1 3.5 5.6"),
        "party" to listOf("M4 20.5l3.8-10.8 7 7Z", "M10.5 11c.8-1.8.5-3.5-.5-5", "M13 13.5c1.8-.8 3.5-.5 5 .5", "M14.5 4l.5 2M19.5 9.5l-2 .5M18 5.5L16.5 7"),
        "flower" to listOf("M8 5l2 2 2-3 2 3 2-2v4a4 4 0 0 1-8 0Z", "M12 13v7.5", "M12 18.5C11.5 15.5 9 14 6 14c0 3 2.5 4.5 6 4.5Z"),
        "charity" to listOf(
            "M12 11.5s-4-2.4-4-5.3a2.2 2.2 0 0 1 4-1.3 2.2 2.2 0 0 1 4 1.3c0 2.9-4 5.3-4 5.3Z",
            "M3.5 13.5h3v7h-3Z", "M6.5 14.5l2.2-1.2a2 2 0 0 1 1-.3h3.3a1.5 1.5 0 0 1 0 3H10",
            "M6.5 19.5h6.3a2 2 0 0 0 1.2-.4l5.3-4a1.5 1.5 0 0 0-1.8-2.4L13.5 15.5",
        ),

        // 休闲
        "film" to listOf(rect(3.5f, 4.5f, 17f, 15f, 2f), "M8 4.5v15M16 4.5v15", "M3.5 9.5H8M3.5 14.5H8M16 9.5h4.5M16 14.5h4.5"),
        "music" to listOf("M9 17.5v-12l11-2v12", "M9 9.5l11-2", ring(6.5f, 17.5f, 2.5f), ring(17.5f, 15.5f, 2.5f)),
        "suitcase" to listOf(rect(5f, 6.5f, 14f, 12.5f, 2.5f), "M9.5 6.5V4h5v2.5", "M9 6.5V19M15 6.5V19", "M8 21h.01M16 21h.01"),
        "mountain" to listOf("M3 19.5l6.5-11 4 6.5 2.5-3.5 5 8Z", "M7.4 12l2.1 1.5 2.15-1.5", ring(16.5f, 6f, 2f)),
        "palm" to listOf(
            "M12.5 20.5c0-3.5.5-7 2-10",
            "M14.5 10.5c-1.5-2.5-4.5-3.5-7.5-2.5M14.5 10.5c1-2.7 3.7-4 6.5-3M14.5 10.5c-2.7.3-5 2-5.5 4.5M14.5 10.5c2.7 0 5 1.5 5.5 4",
            "M5 20.5h14",
        ),
        "book" to listOf("M5 18.5V5a2 2 0 0 1 2-2h12v13.5H7a2 2 0 0 0 0 4h12", "M10 3v6l2-1.5L14 9V3"),
        "star" to listOf("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9Z"),
        "flag" to listOf("M5.5 20.5v-17", "M5.5 4.5h12l-2.5 4 2.5 4h-12"),
        "tree" to listOf("M12 3l-6 7.5h3l-4 6h14l-4-6h3Z", "M12 16.5v4"),
        "sun" to listOf(ring(12f, 12f, 4f), "M12 3v1.5M12 19.5V21M3 12h1.5M19.5 12H21M5.6 5.6l1.1 1.1M17.3 17.3l1.1 1.1M5.6 18.4l1.1-1.1M17.3 6.7l1.1-1.1"),

        // 金融
        "card" to listOf(rect(3f, 5.5f, 18f, 13f, 2.5f), "M3 10h18", "M6.5 14.5h3"),
        "wallet" to listOf(rect(3.5f, 6.5f, 17f, 13.5f, 2.5f), "M5.5 6.5L15 3.5l1.5 3", "M20.5 11H17a2 2 0 0 0 0 4h3.5"),
        "coins" to listOf(
            "M5 7c0-1.4 3.1-2.5 7-2.5s7 1.1 7 2.5-3.1 2.5-7 2.5S5 8.4 5 7Z",
            "M5 7v5c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V7", "M5 12v5c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-5",
        ),
        "piggy" to listOf(
            "M19 11.5c-.6-3-3.6-5-7.5-5-4.1 0-7 2.5-7 6 0 1.8.8 3.3 2 4.4V20h3v-1.5c.6.2 1.3.2 2 .2s1.4 0 2-.2V20h3v-3.1c.8-.6 1.4-1.4 1.8-2.4h1.7v-3Z",
            "M10 9.5h3", "M15.5 10.5h.01", "M4.5 12c-1 0-1.5-.8-1.5-1.5",
        ),
        "bank" to listOf("M3.5 9.5L12 4l8.5 5.5Z", "M6 11.5v6M10 11.5v6M14 11.5v6M18 11.5v6", "M3.5 20h17"),
        "invoice" to listOf("M14 3H6.5A1.5 1.5 0 0 0 5 4.5V19a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 19V8Z", "M14 3v5h5", "M8.5 12.5h7M8.5 16h4.5"),
        "tax" to listOf("M6 3h12v18l-3-2-3 2-3-2-3 2Z", "M9 14.5l6-6", "M9.3 9h.01M14.7 14h.01"),
        "shield" to listOf("M12 3.5l7 2.5v5.5c0 4.5-3 7.8-7 9.5-4-1.7-7-5-7-9.5V6Z", "M9 12l2 2 4-4"),
        "refund" to listOf("M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9", "M4.5 4.5V9H9", "M9.5 9l2.5 3 2.5-3M12 12v4.5M10 13.5h4"),
        "interest" to listOf(ring(7.5f, 7.5f, 2.5f), ring(16.5f, 16.5f, 2.5f), "M18.5 5.5l-13 13"),
        "tag" to listOf("M4 4h7.5l8.5 8.5-7.5 7.5L4 11.5Z", ring(8.5f, 8.5f, 1.5f)),
        "clock" to listOf(ring(12f, 12f, 8.5f), "M12 7.5V12l3 2"),
        "reimburse" to listOf("M14 3H6.5A1.5 1.5 0 0 0 5 4.5V19a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 19V8Z", "M14 3v5h5", "M8.5 14l2.5 2.5 4.5-4.5"),
    )

    /** The picker's sections, in display order. Includes built-in glyph keys too. */
    val groups: List<Group> = listOf(
        Group("常用", listOf("food", "transit", "shop", "daily", "fun", "home", "med", "gift", "study", "other", "salary", "bonus", "invest")),
        Group("餐饮", listOf("coffee", "milk-tea", "noodles", "burger", "pizza", "cake", "fruit", "vegetable", "snack", "wine", "beer")),
        Group("出行", listOf("car", "taxi", "bus", "train", "bike", "plane", "ship", "fuel", "parking")),
        Group("购物", listOf("shirt", "shoes", "handbag", "lipstick", "diamond", "glasses", "watch", "cart", "box")),
        Group("居家", listOf("key", "building", "bolt", "drop", "flame", "wifi", "sofa", "laundry", "wrench", "cigarette")),
        Group("数码办公", listOf("laptop", "phone", "monitor", "headphones", "gamepad", "camera", "subscribe", "pen", "gradcap", "briefcase", "printer")),
        Group("健康", listOf("pill", "hospital", "pulse", "syringe", "tooth", "dumbbell", "scissors", "spa")),
        Group("人情", listOf("hongbao", "heart", "wedding", "baby", "paw", "friends", "party", "flower", "charity")),
        Group("休闲", listOf("film", "music", "suitcase", "mountain", "palm", "book", "star", "flag", "tree", "sun")),
        Group("金融", listOf("card", "wallet", "coins", "piggy", "bank", "invoice", "tax", "shield", "refund", "interest", "tag", "clock", "reimburse")),
    )
}
