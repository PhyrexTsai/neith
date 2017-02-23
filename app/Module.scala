import com.google.inject.AbstractModule

/**
  * Created by carl.huang on 2/21/17.
  */
class Module extends AbstractModule {

  override def configure() = {
    install(new me.mig.playcommon.Module)
  }

}
