import javax.inject.Inject

import me.mig.neith.filters.LoggingFilter
import play.api.http.DefaultHttpFilters

/**
  * Created by phyrextsai on 2017/1/23.
  */
class Filters @Inject()(log: LoggingFilter) extends DefaultHttpFilters(log)
