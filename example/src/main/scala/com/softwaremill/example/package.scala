package com.softwaremill

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete

package object example {
  def completeOK = complete(StatusCodes.OK)

}
