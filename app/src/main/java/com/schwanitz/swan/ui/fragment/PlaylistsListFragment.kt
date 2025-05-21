package com.schwanitz.swan.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.databinding.FragmentPlaylistsListBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.ui.adapter.PlaylistAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class PlaylistsListFragment : Fragment() {

    private var _binding: FragmentPlaylistsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: PlaylistAdapter
    private val searchQuery = MutableStateFlow<String?>(null)
    private val TAG = "PlaylistsListFragment"
    private var allPlaylists: List<PlaylistEntity> = emptyList()
    private var currentExportPlaylist: PlaylistEntity? = null
    private var selectedExportFormat: String = "m3u" // Default-Format

    // Für den Export-Dialog mit benutzerdefiniertem Startverzeichnis
    private val createDocumentWithInitialUri = registerForActivityResult(object : ActivityResultContracts.CreateDocument("*/*") {
        override fun createIntent(context: android.content.Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            val initialUri = getHomeDirectoryUri()
            if (initialUri != null) {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            return intent
        }
    }) { uri ->
        uri?.let { exportPlaylist(it, selectedExportFormat) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)

        adapter = PlaylistAdapter(
            playlists = emptyList(),
            onItemLongClick = { playlist, view ->
                // Zeige ein PopupMenu an
                showPopupMenu(view, playlist)
            }
        )

        binding.playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlaylistsListFragment.adapter
        }

        // Beobachte Playlists aus der Datenbank
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists()
                .collectLatest { playlists ->
                    Log.d(TAG, "Received ${playlists.size} playlists from database")
                    allPlaylists = playlists.sortedBy { it.name.lowercase() }
                    applyFilter(searchQuery.value)
                }
        }

        // Beobachte Suchbegriff-Änderungen
        viewLifecycleOwner.lifecycleScope.launch {
            searchQuery.collectLatest { query ->
                Log.d(TAG, "Search query changed: $query")
                applyFilter(query)
            }
        }

        // FAB-Logik
        showFab()
    }

    // Methode zum Erhalten des Home-Verzeichnis URI
    private fun getHomeDirectoryUri(): Uri? {
        return try {
            // Versuchen Sie es zuerst mit dem Download-Verzeichnis, das oft zugänglich ist
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            if (downloadDir.exists()) {
                // Auf neueren Android-Versionen verwenden wir DocumentsContract für den URI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Für neuere Android-Versionen verwenden wir einen internen URI für den Download-Ordner
                    return Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                } else {
                    // Für ältere Versionen können wir direkt eine Datei-URI verwenden
                    return Uri.fromFile(downloadDir)
                }
            } else {
                // Fallback auf den externen Speicher-Stammordner, wenn Download nicht verfügbar ist
                val externalStorage = Environment.getExternalStorageDirectory()
                if (externalStorage.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        return Uri.parse("content://com.android.externalstorage.documents/document/primary:")
                    } else {
                        return Uri.fromFile(externalStorage)
                    }
                }
            }

            // Wenn alles fehlschlägt, null zurückgeben und das System entscheiden lassen
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting home directory URI: ${e.message}", e)
            null
        }
    }

    // Methode zum Anzeigen des FAB
    private fun showFab() {
        val fab = activity?.findViewById<View>(R.id.fab)
        fab?.visibility = View.VISIBLE
        fab?.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun showPopupMenu(view: View, playlist: PlaylistEntity) {
        val popup = PopupMenu(requireContext(), view)
        // Umbenennen als ersten Eintrag hinzufügen
        popup.menu.add(0, R.id.rename_playlist, 0, R.string.rename_playlist)
        // Exportieren als zweiten Eintrag
        popup.menu.add(0, R.id.export_playlist, 1, R.string.export_playlist)
        // Löschen als dritten Eintrag
        popup.menu.add(0, R.id.delete_playlist, 2, R.string.delete_playlist)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.rename_playlist -> {
                    showRenamePlaylistDialog(playlist)
                    true
                }
                R.id.export_playlist -> {
                    currentExportPlaylist = playlist
                    showExportFormatDialog(playlist)
                    true
                }
                R.id.delete_playlist -> {
                    showDeleteConfirmationDialog(playlist)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showRenamePlaylistDialog(playlist: PlaylistEntity) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.rename_playlist_title)

        val input = EditText(requireContext())
        input.setText(playlist.name)
        input.setPadding(32, 16, 32, 16)
        input.setSelectAllOnFocus(true)
        input.requestFocus()
        builder.setView(input)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                renamePlaylist(playlist.id, newName)
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.create_playlist_error_empty,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null)

        val dialog = builder.create()

        // Zeige die Tastatur automatisch
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        dialog.show()
    }

    private fun renamePlaylist(playlistId: String, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.renamePlaylist(playlistId, newName)
                Toast.makeText(requireContext(), R.string.rename_playlist_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming playlist: ${e.message}", e)
                Toast.makeText(requireContext(), R.string.rename_playlist_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportFormatDialog(playlist: PlaylistEntity) {
        val formats = arrayOf(
            getString(R.string.export_format_m3u),
            getString(R.string.export_format_pls)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.export_playlist_title)
            .setItems(formats) { _, which ->
                when (which) {
                    0 -> { // M3U
                        selectedExportFormat = "m3u"
                        createDocumentWithInitialUri.launch("${playlist.name}.m3u")
                    }
                    1 -> { // PLS
                        selectedExportFormat = "pls"
                        createDocumentWithInitialUri.launch("${playlist.name}.pls")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportPlaylist(uri: Uri, format: String) {
        val playlist = currentExportPlaylist ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Lade alle Songs der Playlist
                val playlistSongs = AppDatabase.getDatabase(requireContext())
                    .playlistDao()
                    .getSongsForPlaylist(playlist.id)
                    .sortedBy { it.position }

                // Konvertiere zu MusicFile-Objekten
                val musicFiles = withContext(Dispatchers.IO) {
                    playlistSongs.mapNotNull { song: PlaylistSongEntity ->
                        val musicFileEntity = AppDatabase.getDatabase(requireContext())
                            .musicFileDao()
                            .getFileByUri(song.songUri)
                            .first() // Konvertiere Flow zu einem einzelnen Wert

                        musicFileEntity?.let { entity ->
                            MusicFile(
                                uri = Uri.parse(entity.uri),
                                name = entity.name,
                                title = entity.title,
                                artist = entity.artist,
                                album = entity.album,
                                albumArtist = entity.albumArtist,
                                discNumber = entity.discNumber,
                                trackNumber = entity.trackNumber,
                                year = entity.year,
                                genre = entity.genre,
                                fileSize = entity.fileSize,
                                audioCodec = entity.audioCodec,
                                sampleRate = entity.sampleRate,
                                bitrate = entity.bitrate,
                                tagVersion = entity.tagVersion
                            )
                        }
                    }
                }

                val contentResolver = requireContext().contentResolver

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                            when (format) {
                                "m3u" -> writeM3UPlaylist(writer, musicFiles)
                                "pls" -> writePLSPlaylist(writer, musicFiles)
                            }
                        }
                    }
                }

                Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting playlist: ${e.message}", e)
                Toast.makeText(requireContext(), R.string.export_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeM3UPlaylist(writer: OutputStreamWriter, musicFiles: List<MusicFile>) {
        writer.write("#EXTM3U\n")

        for (file in musicFiles) {
            // EXTINF Format: Dauer in Sekunden, Künstler - Titel
            val duration = -1 // Dauer nicht bekannt, -1 ist ein Standardwert für unbekannt
            val artist = file.artist ?: ""
            val title = file.title ?: file.name

            writer.write("#EXTINF:$duration,$artist - $title\n")

            // Relativer Pfad aus dem URI extrahieren
            val path = getRelativePath(file.uri)
            writer.write("$path\n")
        }
    }

    private fun writePLSPlaylist(writer: OutputStreamWriter, musicFiles: List<MusicFile>) {
        writer.write("[playlist]\n")
        writer.write("NumberOfEntries=${musicFiles.size}\n")

        for ((index, file) in musicFiles.withIndex()) {
            val entryNumber = index + 1
            val path = getRelativePath(file.uri)
            val title = file.title ?: file.name
            val length = -1 // Länge nicht bekannt, -1 ist ein Standardwert für unbekannt

            writer.write("File$entryNumber=$path\n")
            writer.write("Title$entryNumber=$title\n")
            writer.write("Length$entryNumber=$length\n")
        }

        writer.write("Version=2\n")
    }

    private fun getRelativePath(uri: Uri): String {
        // Extrahiere den relativen Pfad aus dem URI
        val path = uri.path ?: return uri.toString()

        // Wenn der Pfad "primary:Music/song.mp3" enthält, wandle ihn in einen relativen Pfad um
        return if (path.contains("primary:")) {
            path.substringAfter("primary:")
        } else {
            path
        }
    }

    private fun showDeleteConfirmationDialog(playlist: PlaylistEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_playlist)
            .setMessage("Möchten Sie die Playlist '${playlist.name}' wirklich löschen?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                deletePlaylist(playlist.id)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun deletePlaylist(playlistId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deletePlaylist(playlistId)
            Log.d(TAG, "Playlist deleted: $playlistId")
        }
    }

    override fun onPause() {
        super.onPause()
        // Verstecke FAB, wenn Fragment nicht sichtbar
        activity?.findViewById<View>(R.id.fab)?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        // Zeige FAB wieder an, wenn Fragment wieder sichtbar wird
        showFab()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateSearchQuery(query: String?) {
        Log.d(TAG, "Updating search query: $query")
        searchQuery.value = query
    }

    fun getPlaylistAdapter(): PlaylistAdapter? {
        return binding.playlistsRecyclerView.adapter as? PlaylistAdapter
    }

    private fun applyFilter(query: String?) {
        val filteredPlaylists = if (query.isNullOrEmpty()) {
            allPlaylists
        } else {
            allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
        }
        Log.d(TAG, "Applying filter with query '$query': ${filteredPlaylists.size} playlists")
        adapter.updatePlaylists(filteredPlaylists)
        binding.playlistsRecyclerView.visibility = if (filteredPlaylists.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (filteredPlaylists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.create_playlist_dialog_title)
        builder.setMessage(R.string.create_playlist_dialog_message)

        val input = android.widget.EditText(requireContext())
        input.hint = getString(R.string.playlist_name_hint)
        input.setPadding(32, 16, 32, 16)
        // Setze den Cursor ans Ende des Texts
        input.setSelectAllOnFocus(true)
        builder.setView(input)

        builder.setPositiveButton(R.string.create_playlist) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.createPlaylist(name, emptyList())
                }
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.create_playlist_error_empty,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        input.requestFocus()

        // Dialog erstellen und die Tastatur anzeigen
        val dialog = builder.create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        dialog.show()
    }

}