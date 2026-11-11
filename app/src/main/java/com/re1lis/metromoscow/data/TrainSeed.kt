package com.re1lis.metromoscow.data

import com.re1lis.metromoscow.R

object TrainSeed {
    val trains = listOf(
        Train("moscow-2024", "Москва-2024", 2024,
            "Самая современная модификация. Улучшенная шумоизоляция, широкие двери, обновлённый интерьер и разъёмы USB Type-C.",
            listOf(R.drawable.moscow24_first, R.drawable.moscow24_second, R.drawable.moscow24_third)),
        Train("moscow-2020", "Москва-2020", 2020,
            "Поезд с дверями шириной 160 см и сквозными переходами. Оснащён интерактивными картами, системой обеззараживания воздуха и USB-зарядками.",
            listOf(R.drawable.moscow20_first, R.drawable.moscow20_second, R.drawable.moscow20_third, R.drawable.moscow20_fourth)),
        Train("moscow", "Москва", 2017,
            "Первый московский поезд со сквозным проходом. Сенсорные мониторы, зарядки для гаджетов, плавный ход и тихая работа двигателей.",
            listOf(R.drawable.moscow_first, R.drawable.moscow_second, R.drawable.moscow_third)),
        Train("oka", "Ока", 2012,
            "Массовая серия поездов с современными системами кондиционирования и отопления. В ряде модификаций есть сквозной проход.",
            listOf(R.drawable.oka_first, R.drawable.oka_second, R.drawable.oka_third)),
        Train("rusich", "Русич", 2003,
            "Разработан для линий с крутыми поворотами и открытых участков. Сочленённая конструкция вагонов обеспечивает хорошую теплоизоляцию.",
            listOf(R.drawable.rusich_first, R.drawable.rusich_second)),
        Train("numbered", "Номерной (81-717/714)", 1976,
            "Один из самых узнаваемых поездов метро. Получил название из-за отсутствия буквенного обозначения и прошёл множество модернизаций.",
            listOf(R.drawable.nums_first, R.drawable.nums_second, R.drawable.nums_third))
    )
}
