package com.cramsan.hirsh.util

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

/** Hands the file to the browser's own download, as if the user had clicked a download link. */
class BrowserFileSaver : FileSaver {

    override suspend fun save(filename: String, bytes: ByteArray): String {
        val array = Int8Array(bytes.size)
        bytes.forEachIndexed { index, byte -> array[index] = byte }
        downloadBlob(array, filename, "application/pdf")
        return filename
    }
}

private fun downloadBlob(bytes: Int8Array, filename: String, mimeType: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
    }""",
)
