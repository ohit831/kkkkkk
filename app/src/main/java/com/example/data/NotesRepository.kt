package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: String = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date())
)

class NotesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rathod_notes_store", Context.MODE_PRIVATE)

    fun getAllNotes(): List<NoteItem> {
        val json = prefs.getString("notes_list", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<NoteItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NoteItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        timestamp = obj.optString("timestamp", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addNote(title: String, content: String): NoteItem {
        val existing = getAllNotes().toMutableList()
        val newNote = NoteItem(
            title = if (title.isBlank()) "Quick Note" else title,
            content = content
        )
        existing.add(0, newNote)
        saveAll(existing)
        return newNote
    }

    fun deleteNote(id: String) {
        val existing = getAllNotes().filter { it.id != id }
        saveAll(existing)
    }

    private fun saveAll(notes: List<NoteItem>) {
        val array = JSONArray()
        for (n in notes) {
            val obj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("timestamp", n.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("notes_list", array.toString()).apply()
    }
}
