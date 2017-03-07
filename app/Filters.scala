import javax.inject.Inject

import me.mig.neith.filters.LoggingFilter
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter

/**
  * Created by phyrextsai on 2017/1/23.
  */
class Filters @Inject()(log: LoggingFilter, corsFilter: CORSFilter) extends DefaultHttpFilters(log, corsFilter)
