package com.app.haven.data.local

import androidx.room.TypeConverter

//room can only store simple types, so the shares map in Expenses is saved as text
//When annotated with @TypeConverter, the converter function is automatically called when it detects
//a Map datatype
class Converters {

    @TypeConverter
    fun sharesToText(shares: Map<String, Long>): String =
        shares.entries.joinToString(",") { "${it.key}:${it.value}" }

    @TypeConverter
    fun textToShares(text: String): Map<String, Long> =
        if (text.isBlank()) emptyMap()
        else text.split(",").mapNotNull { part ->
            val pieces = part.split(":")
            val cents = pieces.getOrNull(1)?.toLongOrNull()
            //skip anything malformed instead of crashing
            if (pieces.size == 2 && cents != null) pieces[0] to cents else null
        }.toMap()
}