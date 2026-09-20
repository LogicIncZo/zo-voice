// Thin adapter over dev.zocomputer:ask — the canonical implementation lives in
// the zo-kotlin SDK (github.com/LogicIncZo/zo-kotlin). Typealiases keep every
// existing call site (ZoApi.ModelInfo, ZoApi.Options, ZoApi.ask, …) unchanged.
package `in`.cashlessconsumer.zovoice.data

typealias ZoApi = dev.zocomputer.ask.ZoApi

typealias ZoException = dev.zocomputer.ask.ZoException

// Kotlin typealiases do not re-export nested classifiers of an object, so the
// three nested types are re-exported at top level.
typealias ModelInfo = dev.zocomputer.ask.ZoApi.ModelInfo

typealias PersonaInfo = dev.zocomputer.ask.ZoApi.PersonaInfo

typealias Options = dev.zocomputer.ask.ZoApi.Options
