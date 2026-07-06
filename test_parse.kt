import com.example.data.rutube.SmartRutubeParser
import org.json.JSONObject
import java.io.File

fun main() {
    val jsonString = File("test.json").readText()
    val jsonObj = JSONObject(jsonString)
    val parsed = SmartRutubeParser.ResponseAnalyzer.parse(jsonObj, "https://rutube.ru/api/metainfo/tv/1178022/video/")
    println("Type: ${parsed.type}")
    parsed.items.forEach { card ->
        println("Card: ${card.javaClass.simpleName}")
        when (card) {
            is SmartRutubeParser.NormalizedCard.VideoCard -> {
                println("  Title: ${card.title}")
                println("  Duration: ${card.duration}")
                println("  ActionUrl: ${card.actionUrl}")
            }
            is SmartRutubeParser.NormalizedCard.TvSeriesCard -> {
                println("  Title: ${card.title}")
            }
            is SmartRutubeParser.NormalizedCard.FolderCard -> {
                println("  Title: ${card.title}")
                println("  ActionUrl: ${card.actionUrl}")
            }
            else -> {}
        }
    }
}
