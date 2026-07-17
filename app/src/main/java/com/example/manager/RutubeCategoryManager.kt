package com.example.manager

import com.example.data.RutubeCategory
import java.util.concurrent.ConcurrentHashMap

class RutubeCategoryManager {

    val dynamicCategoryTargets = ConcurrentHashMap<String, String>()

    val defaultCategories = listOf(
        RutubeCategory(1001, "Фильмы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/movies/"),
        RutubeCategory(1002, "Сериалы", "https://pic.rtbcdn.ru/promoitem/55/c1/55c106e53da7e36f144347990cb39885.png", "/api/feeds/serials/"),
        RutubeCategory(1003, "Телепередачи", "https://pic.rtbcdn.ru/promoitem/11/4b/114b0e5e339a889c31ee106e9b986c53.png", "/api/feeds/tv/"),
        RutubeCategory(2001, "Онлайн ТВ", "https://pic.rtbcdn.ru/promoitem/3f/e6/3fe614a9cfa0e1c2f25d71102558109d.png", "/api/feeds/tvchannels/"),
        RutubeCategory(2003, "Реалити", "https://pic.rtbcdn.ru/promoitem/12/0a/120a7630bcb1ca858abd529d474fc35d.png", "/api/feeds/reality/"),
        RutubeCategory(2004, "Ток-шоу", "https://pic.rtbcdn.ru/promoitem/11/4b/114b0e5e339a889c31ee106e9b986c53.png", "/api/feeds/talk-show/"),
        RutubeCategory(1004, "Мультфильмы", "https://pic.rtbcdn.ru/promoitem/2025-06-06/64/73/64734dadd906cefa63183b96cd260e1b.png", "/api/feeds/cartoons/"),
        RutubeCategory(1006, "Спорт", "https://pic.rtbcdn.ru/promoitem/dc/04/dc049d8eb246cec4eeb63c615d302bd4.png", "/api/feeds/sport/"),
        RutubeCategory(1007, "Юмор", "https://pic.rtbcdn.ru/promoitem/12/0a/120a7630bcb1ca858abd529d474fc35d.png", "/api/feeds/umor/"),
        RutubeCategory(1008, "Видеоигры", "https://pic.rtbcdn.ru/promoitem/8b/57/8b57e8c2550b1a269b028f6567bbffe6.png", "/api/feeds/games/"),
        RutubeCategory(1009, "Технологии", "https://pic.rtbcdn.ru/promoitem/2025-03-19/a3/c6/a3c653afccb951f5a62bb80c65319a2d.png", "/api/feeds/technologies/"),
        RutubeCategory(1010, "Блоги", "https://pic.rtbcdn.ru/promoitem/ae/62/ae62f83e8cb442661a825819fdf61d8c.png", "/api/feeds/blogs/"),
        RutubeCategory(1012, "Лайфхаки", "https://pic.rtbcdn.ru/promoitem/2025-03-19/a3/c6/a3c653afccb951f5a62bb80c65319a2d.png", "/api/feeds/lifehacks/"),
        RutubeCategory(1013, "Детям", "https://pic.rtbcdn.ru/promoitem/eb/a3/eba3273e49348d87f585dea09b68327e.png", "/api/feeds/kids/"),
        RutubeCategory(1015, "Обучение", "https://pic.rtbcdn.ru/promoitem/15/8d/158d95d4cb03f4187226734c611cfbbe.png", "/api/feeds/education/"),
        RutubeCategory(1016, "Путешествия", "https://pic.rtbcdn.ru/promoitem/24/95/2495ff72ab1d9a70411f2ee8ef6c3b5f.png", "/api/feeds/travel/"),
        RutubeCategory(1017, "Кулинария", "https://pic.rtbcdn.ru/promoitem/02/50/0250d5124179a913e26eb8965f48228e.png", "/api/feeds/food/"),
        RutubeCategory(1018, "Аниме", "https://pic.rtbcdn.ru/promoitem/1d/59/1d59b21c708d89744b20d9f220a47b1a.png", "/api/feeds/anime/")
    )

    init {
        for (defaultCat in defaultCategories) {
            val slug = defaultCat.target
                ?.replace("/api/feeds/", "")
                ?.replace("/feeds/", "")
                ?.replace("/api/v1/feeds/", "")
                ?.trim('/') ?: ""
            if (slug.isNotBlank()) {
                dynamicCategoryTargets[defaultCat.title] = slug
            }
        }
    }
}
