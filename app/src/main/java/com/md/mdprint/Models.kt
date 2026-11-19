package com.md.mdprint

import java.io.File

data class Printer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 631,
    val path: String = "ipp/print",
    val manual: Boolean = false
) {
    val uri: String get() = "ipp://$host:$port/${path.trimStart('/')}"
    val httpsUrl: String get() = "https://$host:$port/${path.trimStart('/')}"
    val httpUrl: String get() = "http://$host:$port/${path.trimStart('/')}"
}

enum class Paper(val label: String, val ipp: String, val wPt: Int, val hPt: Int) {
    A4("A4", "iso_a4_210x297mm", 595, 842),
    LETTER("Letter", "na_letter_8.5x11in", 612, 792),
    LEGAL("Legal", "na_legal_8.5x14in", 612, 1008),
    A5("A5", "iso_a5_148x210mm", 420, 595),
    PHOTO("4×6 Photo", "na_index-4x6_4x6in", 288, 432)
}

enum class Orient(val label: String) { AUTO("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

enum class Quality(val label: String, val ipp: Int) { DRAFT("Draft", 3), NORMAL("Normal", 4), HIGH("Best", 5) }

enum class Sides(val label: String, val ipp: String) {
    ONE("Single", "one-sided"),
    LONG("Duplex", "two-sided-long-edge"),
    SHORT("Duplex flip", "two-sided-short-edge")
}

data class PrintSettings(
    val copies: Int = 1,
    val paper: Paper = Paper.A4,
    val color: Boolean = true,
    val orientation: Orient = Orient.AUTO,
    val sides: Sides = Sides.ONE,
    val asImage: Boolean = false,
    val quality: Quality = Quality.NORMAL
)

data class HistoryItem(
    val id: Long,
    val name: String,
    val kind: String,
    val target: String,
    val ok: Boolean
)

data class Doc(
    val name: String,
    val kind: String,          // "PDF", "Photo", "Text"
    val file: File? = null,
    val files: List<File> = emptyList(),
    val text: String? = null
)

enum class Screen { Main, Setup, Web, Text }
enum class Tab(val emoji: String, val label: String) {
    HOME("🏠", "Home"), PRINTERS("🖨️", "Printers"), HISTORY("🕘", "History"), SETTINGS("⚙️", "Settings")
}
