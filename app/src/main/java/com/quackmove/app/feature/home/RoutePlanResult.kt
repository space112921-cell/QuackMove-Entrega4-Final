package com.quackmove.app.feature.home

import com.quackmove.app.data.repository.RouteSummary
import com.quackmove.app.domain.BatteryEstimate

data class RoutePlanResult(
    val route: RouteSummary,
    val battery: BatteryEstimate
)
