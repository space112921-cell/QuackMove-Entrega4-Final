package com.quackmove.app.data.repository

import com.quackmove.app.domain.Vehicle

// =====================================
// CATALOGO DE CARROS ELECTRICOS
// =====================================
//
// usableBatteryKwh: capacidad útil (neta) de la batería.
// consumptionKwhPer100Km: consumo estimado en uso real
// (mixto ciudad/carretera), no el valor de laboratorio.
// Son estimaciones para planificar viajes, no datos oficiales.

object VehicleCatalog {

    val vehicles: List<Vehicle> = listOf(

        Vehicle("byd-dolphin-mini", "BYD", "Dolphin Mini", 38.0, 13.5),
        Vehicle("byd-dolphin", "BYD", "Dolphin", 60.4, 15.8),
        Vehicle("byd-atto-3", "BYD", "Atto 3", 60.5, 17.5),
        Vehicle("byd-seal", "BYD", "Seal", 82.5, 17.0),

        Vehicle("chevrolet-bolt-euv", "Chevrolet", "Bolt EUV", 65.0, 17.0),

        Vehicle("hyundai-kona-electric", "Hyundai", "Kona Electric", 64.8, 16.0),
        Vehicle("hyundai-ioniq-5", "Hyundai", "Ioniq 5", 74.0, 18.5),

        Vehicle("jac-e-js1", "JAC", "E-JS1", 30.2, 13.0),

        Vehicle("kia-ev6", "Kia", "EV6", 74.0, 18.0),

        Vehicle("mg-4", "MG", "4 Electric", 61.7, 16.5),

        Vehicle("nissan-leaf", "Nissan", "Leaf", 39.0, 16.5),

        Vehicle("peugeot-e-2008", "Peugeot", "e-2008", 50.8, 17.0),
        Vehicle("peugeot-e-408", "Peugeot", "e-408", 58.2, 16.4),

        Vehicle("tesla-model-3", "Tesla", "Model 3", 57.5, 14.5),
        Vehicle("tesla-model-y", "Tesla", "Model Y", 60.0, 16.0),

        Vehicle("volvo-ex30", "Volvo", "EX30", 51.0, 17.0)
    )
}
