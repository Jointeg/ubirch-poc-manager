package com.ubirch

object CommonConf {
  object Lifecycle {
    val TIMEOUT = "system.lifecycle.generalTimeout"
  }
  object PrometheusMetrics {
    val PORT = "system.metrics.prometheus.port"
  }
  object HttpServerConfPaths {
    val PORT = "system.server.port"
    val SWAGGER_PATH = "system.server.swaggerPath"
  }
  object ExecutionContextConfPaths {
    val THREAD_POOL_SIZE = "system.executionContext.threadPoolSize"
  }
}
