package com.drenther.upi_pay

import androidx.annotation.NonNull
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import android.app.Activity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener


/** UpiPayPlugin */
class UpiPayPlugin: FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener {
  private lateinit var channel : MethodChannel
  private var activity: Activity? = null
  private var requestCodeNumber = 201119

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "upi_pay")
    channel.setMethodCallHandler(this)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    binding.addActivityResultListener(this)
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    binding.addActivityResultListener(this)
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {}

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "initiateTransaction" -> this.initiateTransaction(call, result)
      "getInstalledUpiApps" -> this.getInstalledUpiApps(call, result)
      else -> result.notImplemented()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == requestCodeNumber) {
      if (resultCode == Activity.RESULT_OK) {
        // Handle the success case if needed
        // You can use data to retrieve any additional information from the result
        // For example, data?.getStringExtra("keyName")
        channel.invokeMethod("transactionSuccess", null)
      } else {
        // Handle the failure case if needed
        channel.invokeMethod("transactionFailed", null)
      }
      return true
    }
    return false
  }


  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun initiateTransaction(call: MethodCall, result: Result) {
    val app: String? = call.argument("app")
    val pa: String? = call.argument("pa")
    val pn: String? = call.argument("pn")
    val mc: String? = call.argument("mc")
    val tr: String? = call.argument("tr")
    val tn: String? = call.argument("tn")
    val am: String? = call.argument("am")
    val cu: String? = call.argument("cu")
    val url: String? = call.argument("url")

    try {
      /*
       * Some UPI apps extract incorrect format VPA due to url encoding of `pa` parameter.
       * For example, the VPA 'abc@upi' gets url encoded as 'abc%40upi' and is extracted as
       * 'abc 40upi' by these apps. The URI building logic is changed to avoid URL encoding
       * of the value of 'pa' parameter. - Reetesh
      */
      var uriStr: String? = "upi://pay?pa=" + pa +
              "&pn=" + Uri.encode(pn) +
              "&tr=" + Uri.encode(tr) +
              "&am=" + Uri.encode(am) +
              "&cu=" + Uri.encode(cu)
      if(url != null) {
        uriStr += ("&url=" + Uri.encode(url))
      }
      if(mc != null) {
        uriStr += ("&mc=" + Uri.encode(mc))
      }
      if(tn != null) {
        uriStr += ("&tn=" + Uri.encode(tn))
      }
      uriStr += "&mode=00" // &orgid=000000"
      val uri = Uri.parse(uriStr)
      // Log.d("upi_pay", "initiateTransaction URI: " + uri.toString())

      val intent = Intent(Intent.ACTION_VIEW, uri)
      intent.setPackage(app)
      if (activity != null) {
        if(activity?.packageManager != null) {
          if (intent.resolveActivity(activity?.packageManager as PackageManager) == null) {
            result.success("activity_unavailable")
            return
          }
        }
        activity?.startActivityForResult(intent, requestCodeNumber)
      }
    } catch (ex: Exception) {
      Log.e("upi_pay", ex.toString())
      result.success("failed_to_open_app")
    }
  }

  private fun getInstalledUpiApps(call: MethodCall, result: Result) {
    val uriBuilder = Uri.Builder()
    uriBuilder.scheme("upi").authority("pay")

    val uri = uriBuilder.build()
    val intent = Intent(Intent.ACTION_VIEW, uri)

    val packageManager = activity?.packageManager

    try {
      val activities = packageManager?.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

      // Handle null elements in the list
      val activityResponse = activities?.mapNotNull {
        val packageName = it.activityInfo.packageName
        val drawable = packageManager?.getApplicationIcon(packageName)
        if (drawable != null) {
          val bitmap = getBitmapFromDrawable(drawable)
          val icon = if (bitmap != null) {
            encodeToBase64(bitmap)
          } else {
            null
          }
          mapOf(
            "packageName" to packageName,
            "icon" to icon,
            "priority" to it.priority,
            "preferredOrder" to it.preferredOrder
          )
        } else {
          null
        }
      }

      // Ensure that activityResponse is non-null before passing it to result.success
      if (activityResponse != null) {
        result.success(activityResponse)
      } else {
        result.success(emptyList<Map<String, Any>>()) // or handle this case as needed
      }
    } catch (ex: Exception) {
      Log.e("upi_pay", ex.toString())
      result.error("getInstalledUpiApps", "exception", ex)
    }
  }


  private fun encodeToBase64(image: Bitmap): String? {
    val byteArrayOS = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
    return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.NO_WRAP)
  }

  private fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
    val bmp: Bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
    drawable.draw(canvas)
    return bmp
  }
}
