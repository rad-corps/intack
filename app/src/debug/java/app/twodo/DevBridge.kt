package app.twodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.twodo.model.SpaceKind
import app.twodo.model.TodoList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug builds only: lets a developer (or Claude) read and change lists from adb, without tapping
 * through the UI. Guarded by the DUMP permission, which the adb shell has and apps can't get.
 * Use it through scripts/dev.sh, e.g.
 *   adb shell am broadcast -n app.twodo.debug/app.twodo.DevBridge -a app.twodo.DEV --es cmd show --es list Dev
 * The answer comes back as the broadcast's result data.
 */
class DevBridge : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TwoDoApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            val reply = runCatching { run(app, intent) }.getOrElse { "error: ${it.message}" }
            pending.resultData = reply
            pending.finish()
        }
    }

    private suspend fun run(app: TwoDoApp, intent: Intent): String {
        val repo = app.repo
        fun arg(name: String) = intent.getStringExtra(name) ?: error("missing --es $name")
        fun list(): TodoList {
            val key = arg("list")
            val lists = repo.lists.value.values
            return lists.firstOrNull { it.id == key }
                ?: lists.filter { it.name.equals(key, ignoreCase = true) }.let { if (it.size > 1) error("more than one list called $key; use its id") else it.firstOrNull() }
                ?: error("no list $key")
        }
        fun itemId(list: TodoList): String {
            val key = arg("item")
            val items = list.visibleItems
            return (items.filter { it.id.startsWith(key) }.takeIf { it.isNotEmpty() } ?: items.filter { it.text.equals(key, ignoreCase = true) })
                .let { if (it.size > 1) error("more than one item matches $key") else it.firstOrNull()?.id }
                ?: error("no item $key in ${list.name}")
        }
        return when (val cmd = arg("cmd")) {
            "lists" -> repo.lists.value.values.sortedBy { it.name }.joinToString("\n") { l ->
                listOfNotNull(l.id, l.kind.name.lowercase(), l.name, l.groupId?.let { "group=$it" }, "archived".takeIf { l.archived }).joinToString("  ")
            }
            "show" -> list().let { l ->
                (listOf("${l.name} (${l.id})") + l.visibleItems.map { i ->
                    if (i.heading) "## ${i.text}  ${i.id}"
                    else "[${if (i.checked) "x" else " "}] ${i.text}  ${i.id}  by ${i.editor}" + i.note?.lines()?.joinToString("") { "\n      $it" }.orEmpty()
                }).joinToString("\n")
            }
            "note" -> list().let { l ->
                val id = itemId(l)
                repo.setNote(l.id, id, arg("text")); synced(app); "noted $id"
            }
            "add" -> list().let { l -> repo.addItem(l.id, arg("text"), heading = intent.getBooleanExtra("heading", false)); synced(app); "added to ${l.name}" }
            "tick", "untick" -> list().let { l ->
                val id = itemId(l)
                repo.setChecked(l.id, id, cmd == "tick"); synced(app); "${cmd}ed $id"
            }
            "newlist" -> {
                val group = repo.lists.value.values.firstOrNull { it.kind == SpaceKind.GROUP && (it.id == arg("group") || it.name.equals(arg("group"), true)) }
                    ?: error("no group ${arg("group")}")
                val made = repo.addToGroup(group.id, arg("name")) ?: error("couldn't add to ${group.name}")
                synced(app); "made ${made.name} ${made.id}"
            }
            else -> error("unknown cmd $cmd; one of lists, show, add, tick, untick, note, newlist")
        }
    }

    /** Keeps sync running for a while after a change, so it reaches the others even with the app closed. */
    private fun synced(app: TwoDoApp) {
        app.sync.acquire()
        CoroutineScope(Dispatchers.Main).launch {
            delay(KEEP_SYNCING_MS)
            app.sync.release()
        }
    }

    private companion object {
        const val KEEP_SYNCING_MS = 60_000L
    }
}
