package com.homeai.homecamtv.ui

/** What an [ActionCard] does when selected. */
enum class ActionType { ADD_CAMERA }

/** A tappable action card shown in its own row on the browse screen. */
data class ActionCard(
    val type: ActionType,
    val title: String,
    val subtitle: String
)

/** Placeholder card used when a row has no cameras. */
data class HintCard(
    val title: String,
    val subtitle: String
)
