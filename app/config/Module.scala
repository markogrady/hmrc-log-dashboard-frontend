package config

import com.google.inject.AbstractModule

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[services.StartupSeeder]).asEagerSingleton()
    // The auth seam: on MDTP this would bind an auth-client-backed implementation instead.
    bind(classOf[controllers.actions.IdentifierAction])
      .to(classOf[controllers.actions.SessionIdentifierAction])
  }
}
