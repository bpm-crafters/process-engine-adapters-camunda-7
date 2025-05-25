package dev.bpmcrafters.processengineapi.adapter.c7.remote.deploy

import dev.bpmcrafters.processengineapi.deploy.NamedResource
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Multipart File based on named resource.
 */
internal class NamedResourceMultipartFile(
  private val resource: NamedResource
) : MultipartFile {
  override fun getInputStream(): InputStream = resource.resourceStream
  override fun getName(): String = resource.name
  override fun getOriginalFilename(): String = resource.name
  override fun getContentType(): String? = null
  override fun isEmpty(): Boolean = resource.resourceStream.available() != 0
  override fun getSize(): Long = resource.resourceStream.available().toLong()
  override fun getBytes(): ByteArray = resource.resourceStream.readBytes()
  override fun transferTo(file: File) {
    var outputStream: OutputStream? = null
    try {
      outputStream = FileOutputStream(file)
      outputStream.write(bytes)
    } finally {
      outputStream?.close()
    }
  }

}
