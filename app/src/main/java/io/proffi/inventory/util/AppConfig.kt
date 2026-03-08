package io.proffi.inventory.util

object AppConfig {
    /**
     * Базовый URL вашего API сервера
     * Замените на актуальный адрес вашего сервера
     * Примеры:
     * - "https://api.example.com/"
     * - "http://192.168.1.100:8080/" (для локального сервера)
     */
     const val BASE_URL = "http://market.proffi.io/"
//    const val BASE_URL = "http://10.0.2.2:8080/"


    /**
     * Таймаут для сетевых запросов (в секундах)
     */
    const val NETWORK_TIMEOUT = 30L
}
