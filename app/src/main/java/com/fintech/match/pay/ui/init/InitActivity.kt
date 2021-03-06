package com.fintech.match.pay.ui.init

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import com.fintech.lxf.helper.debug
import com.fintech.lxf.helper.toast
import com.fintech.match.pay.R
import com.fintech.match.pay.api.ApiProducerModule
import com.fintech.match.pay.api.ApiService
import com.fintech.match.pay.api.Constants
import com.fintech.match.pay.api.Constants.KEY_USER_NAME
import com.fintech.match.pay.common.Configuration
import com.fintech.match.pay.data.db.DB
import com.fintech.match.pay.data.db.User
import com.fintech.match.pay.helper.SPHelper
import com.fintech.match.pay.service.init.*
import com.fintech.match.pay.ui.login.LoginActivity
import com.fintech.match.ui.BaseActivity
import com.opencsv.CSVWriter
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_init.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

class InitActivity : BaseActivity() {
    private var startType = 0
    private var last: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init)

        if (Configuration.noLogin()) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("type", 2)
            startActivity(intent)
            finish()
        } else {
//            Constants.baseUrl = "https://" + Configuration.getUserInfoByKey(Constants.KEY_ADDRESS) + "/"
            Constants.baseUrl = Configuration.getUserInfoByKey(Constants.KEY_ADDRESS) + "/"
        }

        startType = intent.getIntExtra("reStart", 0)

        SPHelper.getInstance().init(this)

        et_ali_startPos.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                BaseAccessibilityService.startPos = s?.toString()?.toIntOrNull() ?: 1
            }

        })

        et_ali_total.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                BaseAccessibilityService.endPos = s?.toString()?.toIntOrNull() ?: 3000
            }

        })
        et_ali_offsetTotal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                BaseAccessibilityService.offsetTotal = s?.toString()?.toIntOrNull() ?: 5
            }

        })
        btnAli.setOnClickListener { startAli() }
        btnCSV.setOnClickListener { toCSV() }
        btnUpload.setOnClickListener { upload() }
//        btnWeChat.setOnClickListener { startWeChat() }
//        btnClearAli.setOnClickListener { clearSqlLocal(BaseAccessibilityService.TYPE_ALI) }
//        btnClearWechat.setOnClickListener { clearSqlLocal(BaseAccessibilityService.TYPE_WeChat) }
    }

    private fun clearSqlLocal(type: Int) {
        Observable
                .create<Int> { emitter ->
                    val datas = DB.queryAll(this, type)
                    if (datas != null) {
                        val result = DB.deleteAll(this, *datas.toTypedArray())
                        emitter.onNext(result)
                    }
                    emitter.onComplete()
                }
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<Int> {
                    var d: Disposable? = null
                    override fun onComplete() {
                        getDataFromSql()
                        d?.dispose()
                    }

                    override fun onSubscribe(d: Disposable) {
                        this.d = d
                    }

                    override fun onNext(t: Int) {
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        d?.dispose()
                    }

                })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startType = intent.getIntExtra("reStart", 0)
    }

    override fun onResume() {
        super.onResume()

        getDataFromSql()
    }

    private fun getDataFromSql() {
        Observable
                .create<User> { emitter ->
                    val ali_last = DB.queryLast(this, BaseAccessibilityService.TYPE_ALI)
                    val wechat_last = DB.queryLast(this, BaseAccessibilityService.TYPE_WeChat)
                    if (ali_last != null)
                        emitter.onNext(ali_last)
                    if (wechat_last != null)
                        emitter.onNext(wechat_last)

                    emitter.onComplete()
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { last ->
                    when (last.type) {
                        BaseAccessibilityService.TYPE_ALI -> {
                            this.last = last
                            et_ali_account.setText(last.account)
                            et_ali_startPos.setText(last.pos_start.toString())
                            et_ali_total.setText(last.pos_end.toString())
                            et_ali_offsetTotal.setText(last.offset_total.toString())
                        }
                    }
                }
                .delay(2000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    debug(localClassName, "startType:$startType")
                    when (startType) {
                        1 -> {
                            btnAli.performClick()
                        }
//                        1000 -> {
//                            AlertDialog.Builder(this@InitActivity)
//                                    .setMessage("出现未知错误，请手动重启")
//                                    .setPositiveButton("确定") { dialog, _ -> dialog?.dismiss() }
//                                    .show()
//                        }
                        2000 -> {
                            SystemClock.sleep(4000)
                            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            am.killBackgroundProcesses("com.eg.android.AlipayGphone")
                            SystemClock.sleep(1000)
                            btnAli.performClick()
                        }
                    }
                    startType = 0
                }
                .subscribe(object : Observer<User> {
                    internal var d: Disposable? = null
                    override fun onSubscribe(d: Disposable) {
                        this.d = d
                    }

                    override fun onNext(last: User) {
                        btnUpload.isEnabled = true
                        btnCSV.isEnabled = true
                        if (last.pos_curr == BaseAccessibilityService.endPos &&
                                last.offset == BaseAccessibilityService.offsetTotal - 1) {
                            AlertDialog.Builder(this@InitActivity)
                                    .setMessage("运行完毕")
                                    .setPositiveButton("确定") { dialog, _ -> dialog?.dismiss() }
                                    .show()
                        }
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        d?.dispose()
                    }

                    override fun onComplete() {
                        d?.dispose()
                    }
                })
    }

    private fun toCSV() {
        Single
                .create(SingleOnSubscribe<List<User>> { emitter ->
                    delLocalCSV()
                    val users = DB.queryAll(this@InitActivity, BaseAccessibilityService.TYPE_ALI)
                    if (users != null)
                        emitter.onSuccess(users)
                })
                .subscribeOn(Schedulers.io())
                .subscribe { users ->
                    val n = users.size / 12000
                    for (i in 0..n) {
                        val start = i * 12000
                        val end = (i + 1) * 12000
                        val users_ = users.subList(start, if (end > users.size) users.size else end)

                        val filePath = Environment.getExternalStorageDirectory().toString() + "/a_match_pay/ali-" + SPHelper.getInstance().getString(AliPayUI.acc) + "-" + i + "-all" + ".txt"
                        val writer = CSVWriter(OutputStreamWriter(FileOutputStream(filePath, true), "GBK"))

                        users_
                                .map {
                                    arrayOf(it.qr_str, ((it.pos_curr * it.multiple - it.offset) / 100.0).toString() + "")
                                }
                                .forEach { writer.writeNext(it) }

                        writer.close()
                    }

                }
    }

    private fun uploadToServer() {
        Observable
                .create<List<User>> { emitter ->
                    delLocalCSV()
                    val users = DB.queryAll(this@InitActivity, BaseAccessibilityService.TYPE_ALI)
                    if (users != null)
                        emitter.onNext(users)
                    emitter.onComplete()
                }
                .subscribeOn(Schedulers.io())
                .flatMap { users ->
                    val files = mutableListOf<String>()
                    val n = users.size / 12000
                    for (i in 0..n) {
                        val start = i * 12000
                        val end = (i + 1) * 12000
                        val users_ = users.subList(start, if (end > users.size) users.size else end)

                        val filePath = Environment.getExternalStorageDirectory().toString() + "/a_match_pay/ali-" + SPHelper.getInstance().getString(AliPayUI.acc) + "-" + i + "-all" + ".txt"
                        val writer = CSVWriter(OutputStreamWriter(FileOutputStream(filePath, true), "GBK"))

                        users_
                                .map {
                                    arrayOf(it.qr_str, ((it.pos_curr * it.multiple - it.offset) / 100.0).toString() + "")
                                }
                                .forEach { writer.writeNext(it) }

                        writer.close()

                        files.add(filePath)
                    }

                    Observable.fromIterable(files)
                }
                .flatMap { path ->
                    val file = File(path)
                    val requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file)
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

//                    val des = "15274936971@003"
                    val des = Configuration.getUserInfoByKey(KEY_USER_NAME)
                    val desBody = RequestBody.create(MediaType.parse("multipart/form-data"), des)
                    val des2 = "2001"
                    val desBody2 = RequestBody.create(MediaType.parse("multipart/form-data"), des2)

                    val map = HashMap<String, RequestBody>()
                    map.put("userName", desBody)
                    map.put("payMethod", desBody2)

                    ApiProducerModule.create(ApiService::class.java)
                            .upload("http://api.trueinfo.cn/api/upload/uploadfile", map, body)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object :Observer<ResponseBody>{
                    var d:Disposable? = null
                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        d?.dispose()
                    }

                    override fun onNext(t: ResponseBody) {
                        if (t.string().contains("success"))
                            toast("提交本地数据成功")
                        else
                            toast("提交本地数据失败")
                    }

                    override fun onComplete() {
                        delLocalCSV()

                        d?.dispose()
                    }

                    override fun onSubscribe(d: Disposable) {
                        this.d = d
                    }
                })
    }

    private fun delLocalCSV() {
        val filePath = Environment.getExternalStorageDirectory().toString() + "/a_match_pay/"
        val file = File(filePath)
        if (file.isDirectory)
            file.listFiles()
                    .forEach { it.delete() }
    }

    private fun upload() {
        AlertDialog.Builder(this)
                .setMessage("确认要上传本地数据吗？")
                .setCancelable(false)
                .setPositiveButton("确认") { _, _ ->
                    uploadToServer()
                }
                .setNegativeButton("取消") { _, _ ->
                }
                .show()
    }

    private fun startAli() {
        val accessibilitySettingsOn = AccessibilityHelper.isAccessibilitySettingsOn(applicationContext, AlipayAccessibilityService::class.java)
        if (!accessibilitySettingsOn) {
            AlertDialog.Builder(this)
                    .setMessage("请先打开支付宝辅助插件。")
                    .setCancelable(false)
                    .setPositiveButton("去打开") { _, _ ->
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        //                        finish()
                    }
                    .show()
            return
        }
        val acc = et_ali_account.text.toString().trim()
        val pos = last?.pos_curr ?: BaseAccessibilityService.startPos
        val offset = last?.offset ?: 0
        val beishu = 100
        val end = et_ali_total.text.toString().trim().toIntOrNull() ?: BaseAccessibilityService.endPos

        SPHelper.getInstance().putString(AliPayUI.acc, acc)
        SPHelper.getInstance().putInt(AliPayUI.posV, pos)
        SPHelper.getInstance().putInt(AliPayUI.startV, pos)
        SPHelper.getInstance().putInt(AliPayUI.endV, end)
        SPHelper.getInstance().putInt(AliPayUI.offsetV, offset)
        SPHelper.getInstance().putInt(AliPayUI.beishuV, beishu)

        AliPayUI.steep = 0

        val filePath = Environment.getExternalStorageDirectory().toString() + "/a_match_pay/"
        val file = File(filePath)
        if (!file.exists()) {
            file.mkdir()
        }
        Utils.launchAlipayAPP(this)


    }

//    private fun startWeChat() {
//        val accessibilitySettingsOn_wechat = AccessibilityHelper.isAccessibilitySettingsOn(applicationContext, WechatAccessibilityService::class.java)
//        if (!accessibilitySettingsOn_wechat) {
//            AlertDialog.Builder(this)
//                    .setMessage("请先打开微信辅助插件。")
//                    .setCancelable(false)
//                    .setPositiveButton("去打开") { _, _ ->
//                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
//                        startActivity(intent)
//                    }
//                    .setNegativeButton("取消") { _, _ ->
//                        //                        finish()
//                    }
//                    .show()
//            return
//        }
//        val acc = et_wx_account.text.toString().trim()
//        val pos = et_wx_pos.text.toString().trim().toIntOrNull() ?: 1
//        val offset = et_wx_offset.text.toString().trim().toIntOrNull() ?: 0
//        val beishu = 100
//        val end = et_wx_total.text.toString().trim().toIntOrNull() ?: 3000
//
//        SPHelper.getInstance().putString(WechatUI.acc, acc)
//        SPHelper.getInstance().putInt(WechatUI.posV, pos)
//        SPHelper.getInstance().putInt(WechatUI.startV, pos)
//        SPHelper.getInstance().putInt(WechatUI.endV, end)
//        SPHelper.getInstance().putInt(WechatUI.offsetV, offset)
//        SPHelper.getInstance().putInt(WechatUI.beishuV, beishu)
//
//        WechatUI.steep = -2
//
//        val filePath = Environment.getExternalStorageDirectory().toString() + "/a_match_pay/"
//        val file = File(filePath)
//        if (!file.exists()) {
//            file.mkdir()
//        }
//        Utils.launchWechatAPP(this)
//    }
}
