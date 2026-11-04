package com.app.haven.data.local

import androidx.room.TypeConverter
import org.json.JSONObject

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

    //drafts: which members are ticked; user ids never contain commas
    @TypeConverter
    fun uidsToText(uids: Set<String>): String = uids.joinToString(",")

    @TypeConverter
    fun textToUids(text: String): Set<String> =
        if (text.isBlank()) emptySet() else text.split(",").filter { it.isNotBlank() }.toSet()

    //drafts: custom amounts exactly as the user typed them; they can contain anything (commas, colons...),
    //so they're stored as JSON, which handles that safely (org.json is built into Android)
    @TypeConverter
    fun textMapToJson(map: Map<String, String>): String = JSONObject(map).toString()

    @TypeConverter
    fun jsonToTextMap(json: String): Map<String, String> =
        if (json.isBlank()) emptyMap()
        else runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
}