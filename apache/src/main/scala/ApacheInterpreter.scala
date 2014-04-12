package httpz
package apachehttp

import java.io.Closeable
import java.net.URI

import org.apache.http.{Header, HttpStatus, HttpEntity, StatusLine}
import org.apache.http.util.EntityUtils
import org.apache.http.client.methods._
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.HttpClients
import org.apache.http.entity.ByteArrayEntity

final case class HttpError(
  status: Int,
  body: String
) extends RuntimeException {
  override def toString = Seq(
    "status" -> status,
    "body" -> body
  ).mkString(getClass.getName + "(", ", ", ")")
}

object ApacheInterpreter extends InterpretersTemplate {

  private def using[A <: Closeable, B](resource: A)(f: A => B): B = try {
    f(resource)
  } finally {
    resource.close
  }

  private def setByteArrayEntity(req: HttpEntityEnclosingRequestBase, body: Option[Array[Byte]]) = {
    body match {
      case Some(bytes) =>
       req.setEntity(new ByteArrayEntity(bytes))
      case None =>
    }
    req
  }

  private def executeRequest(req: httpz.Request) = {
    val uri = buildURI(req)
    val request = req.method match {
      case "GET" =>
        new HttpGet(uri)
      case "HEAD" =>
        new HttpHead(uri)
      case "DELETE" =>
        new HttpDelete(uri)
      case "OPTIONS" =>
        new HttpOptions(uri)
      case "TRACE" =>
        new HttpTrace(uri)
      case other =>
        val r = other match {
          case "PUT" => new HttpPut(uri)
          case "PATCH" => new HttpPatch(uri)
          case "POST" => new HttpPost(uri)
        }
        setByteArrayEntity(r, req.body)
    }
    val c = HttpClients.createDefault()
    req.basicAuth.foreach{ case (user, pass) =>
      val creds = new UsernamePasswordCredentials(user, pass)
      val context = HttpClientContext.create()
      request.addHeader(new BasicScheme().authenticate(creds, request, context))
    }
    req.headers.foreach{ case (k, v) => request.addHeader(k, v) }
    c.execute(request)
  }

  override protected def request2string(req: httpz.Request) = {
    using(executeRequest(req)){ res =>
      val code = res.getStatusLine().getStatusCode()
      val entity = res.getEntity()
      val body = EntityUtils.toString(entity, "UTF-8")
      if (code == HttpStatus.SC_OK) {
        body
      } else {
        throw new HttpError(code, body)
      }
    }
  }

  private def buildURI(req: httpz.Request): URI = {
    val uriBuilder = new org.apache.http.client.utils.URIBuilder(req.url)
    req.params.foreach{ case (key, value) =>
      uriBuilder.addParameter(key, value)
    }
    uriBuilder.build
  }

}

