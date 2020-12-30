package com.folioreader.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.folioreader.Config
import com.folioreader.FolioReader
import com.folioreader.R
import com.folioreader.mediaoverlay.MediaController
import com.folioreader.mediaoverlay.MediaControllerCallbacks
import com.folioreader.model.HighLight
import com.folioreader.model.HighlightImpl
import com.folioreader.model.event.*
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.SearchLocator
import com.folioreader.model.sqlite.HighLightTable
import com.folioreader.ui.activity.FolioActivityCallback
import com.folioreader.ui.base.HtmlTask
import com.folioreader.ui.base.HtmlTaskCallback
import com.folioreader.ui.base.HtmlUtil
import com.folioreader.ui.view.FolioWebView
import com.folioreader.ui.view.LoadingView
import com.folioreader.ui.view.VerticalSeekbar
import com.folioreader.ui.view.WebViewPager
import com.folioreader.util.AppUtil
import com.folioreader.util.HighlightUtil
import com.folioreader.util.UiUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import java.util.*
import java.util.regex.Pattern
import android.app.AlertDialog;
import android.widget.Toast
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import kotlinx.android.synthetic.main.login.*
import kotlinx.android.synthetic.main.tooltip_second.*
import android.view.WindowManager
import android.view.Window;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams
import com.folioreader.ui.view.ConfigBottomSheetDialogFragment
// import android.support.v4.app.FragmentManager;
import androidx.fragment.app.FragmentManager;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import com.folioreader.Constants
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Timer
import kotlin.concurrent.schedule



/**
 * Created by mahavir on 4/2/16.
 */
class FolioPageFragment : Fragment(),
    HtmlTaskCallback, MediaControllerCallbacks, FolioWebView.SeekBarListener {

    companion object {

        @JvmField
        val LOG_TAG: String = FolioPageFragment::class.java.simpleName

        private const val BUNDLE_SPINE_INDEX = "BUNDLE_SPINE_INDEX"
        private const val BUNDLE_BOOK_TITLE = "BUNDLE_BOOK_TITLE"
        private const val BUNDLE_SPINE_ITEM = "BUNDLE_SPINE_ITEM"
        private const val BUNDLE_READ_LOCATOR_CONFIG_CHANGE = "BUNDLE_READ_LOCATOR_CONFIG_CHANGE"
        const val BUNDLE_SEARCH_LOCATOR = "BUNDLE_SEARCH_LOCATOR"

        @JvmStatic
        fun newInstance(spineIndex: Int, bookTitle: String, spineRef: Link, bookId: String, link: String, statusTooltip: String, enableChap: String): FolioPageFragment {
            val fragment = FolioPageFragment()
            val args = Bundle()
            args.putInt(BUNDLE_SPINE_INDEX, spineIndex)
            args.putString(BUNDLE_BOOK_TITLE, bookTitle)
            args.putString(FolioReader.EXTRA_BOOK_ID, bookId)
            args.putString(FolioReader.EXTRA_BOOK_ID2, bookId)
            args.putString(FolioReader.EXTRA_LINK, link)
            args.putString(FolioReader.EXTRA_STATUS_TOOLTIP, statusTooltip)
            args.putSerializable(BUNDLE_SPINE_ITEM, spineRef)
            args.putString(FolioReader.EXTRA_CHAP_ENABLE, enableChap)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var uiHandler: Handler
    private var mHtmlString: String? = null
    private val hasMediaOverlay = false
    private var mAnchorId: String? = null
    private var rangy = ""
    private var highlightId: String? = null

    private var lastReadLocator: ReadLocator? = null
    private var outState: Bundle? = null
    private var savedInstanceState: Bundle? = null

    private var mRootView: View? = null
    private var mRootView2: View? = null

    private var loadingView: LoadingView? = null
    private var mScrollSeekbar: VerticalSeekbar? = null
    var mWebview: FolioWebView? = null
    var webViewPager: WebViewPager? = null
    private var mPagesLeftTextView: TextView? = null
    private var mMinutesLeftTextView: TextView? = null
    private var mActivityCallback: FolioActivityCallback? = null

    private var mTotalMinutes: Int = 0
    var positonXBlockFrag: Int = 0
    private var mFadeInAnimation: Animation? = null
    private var mFadeOutAnimation: Animation? = null

    lateinit var spineItem: Link
    private var spineIndex = -1
    private var mBookTitle: String? = null
    private var mIsPageReloaded: Boolean = false
    var mIsShowRemindPurchase: Boolean = false
    private var mIsBlockToggleMenu: Boolean = false
    private var popupShowed: Boolean = false
    private var test: Boolean = false
    var isFinishChapToday: Boolean = false
    var isNavigate: Boolean = false
    var shouldBlock: Boolean = false
    var isHorizontal: Boolean = true

    private var highlightStyle: String? = null

    private var mediaController: MediaController? = null
    private var mConfig: Config? = null
    private var mBookId: String? = null
    private var mLink: String? = null
    private var mEnableChap: String? = null
    private var mStatusTooltip: String? = null
    var searchLocatorVisible: SearchLocator? = null

    var horizonX: Int = 0
    var horizonY: Int = 0
    var vertiX: Int = 0
    var vertiY: Int = 0
    var scrollHeight: Int = 0

    private lateinit var chapterUrl: Uri

    val pageName: String
        get() = mBookTitle + "$" + spineItem.href

    private val isCurrentFragment: Boolean
        get() {
            return isAdded && mActivityCallback!!.currentChapterIndex == spineIndex
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        shouldBlock = false
        isNavigate = false
        this.savedInstanceState = savedInstanceState
        uiHandler = Handler()

        if (activity is FolioActivityCallback)
            mActivityCallback = activity as FolioActivityCallback?

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        spineIndex = arguments!!.getInt(BUNDLE_SPINE_INDEX)
        mBookTitle = arguments!!.getString(BUNDLE_BOOK_TITLE)
        spineItem = arguments!!.getSerializable(BUNDLE_SPINE_ITEM) as Link
        mBookId = arguments!!.getString(FolioReader.EXTRA_BOOK_ID)
        mLink = arguments!!.getString(FolioReader.EXTRA_LINK)
        mEnableChap = arguments!!.getString(FolioReader.EXTRA_CHAP_ENABLE)
        mStatusTooltip = arguments!!.getString(FolioReader.EXTRA_STATUS_TOOLTIP)
        chapterUrl = Uri.parse(mActivityCallback?.streamerUrl + spineItem.href!!.substring(1))

        searchLocatorVisible = savedInstanceState?.getParcelable(BUNDLE_SEARCH_LOCATOR)

        if (spineItem != null) {
            // SMIL Parsing not yet implemented in r2-streamer-kotlin
            //if (spineItem.getProperties().contains("media-overlay")) {
            //    mediaController = new MediaController(getActivity(), MediaController.MediaType.SMIL, this);
            //    hasMediaOverlay = true;
            //} else {
            mediaController = MediaController(activity, MediaController.MediaType.TTS, this)
            mediaController!!.setTextToSpeech(activity)
            //}
        }
        highlightStyle = HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
        mRootView = inflater.inflate(R.layout.folio_page_fragment, container, false)
        mPagesLeftTextView = mRootView!!.findViewById<View>(R.id.pagesLeft) as TextView
        mMinutesLeftTextView = mRootView!!.findViewById<View>(R.id.minutesLeft) as TextView

        mConfig = AppUtil.getSavedConfig(context)

        loadingView = mRootView!!.findViewById(R.id.loadingView)
        initSeekbar()
        initAnimations()
        initWebView()
        updatePagesLeftTextBg()



        return mRootView
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when pause/play
     * button is clicked
     *
     * @param event of type [MediaOverlayPlayPauseEvent] contains if paused/played
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun pauseButtonClicked(event: MediaOverlayPlayPauseEvent) {
        if (isAdded && spineItem!!.href == event.href) {
            mediaController!!.stateChanged(event)
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when speed
     * change buttons are clicked
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected speed
     * type HALF,ONE,ONE_HALF and TWO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun speedChanged(event: MediaOverlaySpeedEvent) {
        if (mediaController != null)
            mediaController!!.setSpeed(event.speed)
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when new
     * style is selected on button click.
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected style
     * of type DEFAULT,UNDERLINE and BACKGROUND.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun styleChanged(event: MediaOverlayHighlightStyleEvent) {
        if (isAdded) {
            when (event.style) {
                MediaOverlayHighlightStyleEvent.Style.DEFAULT -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
                MediaOverlayHighlightStyleEvent.Style.UNDERLINE -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.DottetUnderline)
                MediaOverlayHighlightStyleEvent.Style.BACKGROUND -> highlightStyle =
                        HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.TextColor)
            }
            mWebview!!.loadUrl(String.format(getString(R.string.setmediaoverlaystyle), highlightStyle))
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered when any EBook configuration is changed.
     *
     * @param reloadDataEvent empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun reload(reloadDataEvent: ReloadDataEvent) {
        isNavigate = false

        if (isCurrentFragment)
            load()

        if (isAdded) {
            mWebview!!.dismissPopupWindow()
            mWebview!!.initViewTextSelection()
            loadingView!!.updateTheme()
            loadingView!!.show()
            mIsPageReloaded = true
            setHtml(true)
            updatePagesLeftTextBg()
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     *
     *
     * Function triggered when highlight is deleted and page is needed to
     * be updated.
     *
     * @param event empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateHighlight(event: UpdateHighlightEvent) {
        if (isAdded) {
            this.rangy = HighlightUtil.generateRangyString(pageName)
            loadRangy(this.rangy)
        }
    }

    fun scrollToAnchorId(href: String) {

        if (!TextUtils.isEmpty(href) && href.indexOf('#') != -1) {
            mAnchorId = href.substring(href.lastIndexOf('#') + 1)
            if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
                loadingView!!.show()
                mWebview!!.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                mAnchorId = null
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun resetCurrentIndex(resetIndex: RewindIndexEvent) {
        if (isCurrentFragment) {
            mWebview!!.loadUrl("javascript:rewindCurrentIndex()")
        }
    }

    override fun onReceiveHtml(html: String) {
        if (isAdded) {
            mHtmlString = html
            setHtml(false)
        }
    }

    private fun setHtml(reloaded: Boolean) {
        if (spineItem != null) {
            /*if (!reloaded && spineItem.properties.contains("media-overlay")) {
                mediaController.setSMILItems(SMILParser.parseSMIL(mHtmlString));
                mediaController.setUpMediaPlayer(spineItem.mediaOverlay, spineItem.mediaOverlay.getAudioPath(spineItem.href), mBookTitle);
            }*/
            mConfig = AppUtil.getSavedConfig(context)

            val href = spineItem.href
            var path = ""
            val forwardSlashLastIndex = href!!.lastIndexOf('/')
            if (forwardSlashLastIndex != -1) {
                path = href.substring(1, forwardSlashLastIndex + 1)
            }

            val mimeType: String =
                if (spineItem.typeLink!!.equals(getString(R.string.xhtml_mime_type), true)) {
                    getString(R.string.xhtml_mime_type)
                } else {
                    getString(R.string.html_mime_type)
                }

            uiHandler.post {
                mWebview!!.loadDataWithBaseURL(
                    mActivityCallback?.streamerUrl + path,
                    HtmlUtil.getHtmlContent(mWebview!!.context, mHtmlString, mConfig!!),
                    mimeType,
                    "UTF-8", null
                )
            }
        }
    }

    fun scrollToLast() {
        val isPageLoading = loadingView == null || loadingView!!.visibility == View.VISIBLE
        if (!isPageLoading) {
            loadingView!!.show()
            mWebview!!.loadUrl("javascript:scrollToLast()")
        }
    }

    fun showRemindPurchase(isLastPage:Boolean = false ) {
            if (mLink?.length!! < 1) {
                return
            }
            mIsShowRemindPurchase = true
            val dialogBuilder = AlertDialog.Builder(getActivity())
            var message = "Bạn có muốn đọc đầy đủ toàn bộ cuốn sách? Xin vui lòng mua ngay tại đây!";
            if (isLastPage) {
                message = "Các chương đọc miễn phí đã hết. Bạn có muốn đọc đầy đủ toàn bộ cuốn sách? Xin vui lòng mua ngay tại đây!"
            }
            dialogBuilder.setMessage(message)
                    // if the dialog is cancelable
                    .setCancelable(true)
                    .setPositiveButton("Mua ngay", DialogInterface.OnClickListener {
                        dialog, id ->
                        val openURL = Intent(android.content.Intent.ACTION_VIEW)
                        openURL.data = Uri.parse(mLink)
                        startActivity(openURL)
                        dialog.dismiss()
                        mIsShowRemindPurchase = false
                    })
                    .setNegativeButton("Để sau", DialogInterface.OnClickListener {
                        dialog, id ->
                        dialog.dismiss()
                        mIsShowRemindPurchase = false
                        scrollToAnchorId("href/OEBPS/Text/cover.xhtml")


                    })
            val alert = dialogBuilder.create()
            alert.setOnCancelListener {  func ->  mActivityCallback!!.hideSystemUI()}

            alert.setTitle("")
            alert.show()
            
    }


    fun showRemindReading() {
        uiHandler.post {

        val datePlusOneMonth = Calendar.getInstance().run {
            add(Calendar.MONTH, 1)
            time
        }
        val current = (LocalDateTime.now()).plusDays(1)

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val formatted = current.format(formatter)

        val dialogBuilder = AlertDialog.Builder(getActivity())
        var message = "Mời bạn đọc phần tiếp theo vào ngày " + formatted;

        dialogBuilder.setMessage(message)
                // if the dialog is cancelable
                .setCancelable(true)
                 .setNegativeButton("Đồng ý", DialogInterface.OnClickListener {
                    dialog, id ->
                    mActivityCallback!!.hideSystemUI()
                    dialog.dismiss()
                        Timer("SettingUp", false).schedule(2500) { 
                           mIsShowRemindPurchase = false
                        }
                })

        val alert = dialogBuilder.create()
        alert.setOnCancelListener {  func -> Timer("SettingUp", false).schedule(2500) { 
                           mIsShowRemindPurchase = false
                           mActivityCallback!!.hideSystemUI()
                        } 
                    }

        alert.setTitle("")
        if (!mIsShowRemindPurchase) {
            mIsShowRemindPurchase = true
                        Log.v(LOG_TAG, "-> ===show")
            alert.show()
                
            }
        }
    }

    fun showGuidePopup() {
        if (getActivity()!=null){
            val mDialogView = LayoutInflater.from(getActivity()).inflate(R.layout.login, null)
            //AlertDialogBuilder
            val mBuilder = AlertDialog.Builder(getActivity())
                    .setView(mDialogView)
                    // .setTitle("Login Form")
            //show dialog
            val  mAlertDialog = mBuilder.show()
            val dialogButton:ImageView = mAlertDialog.findViewById(R.id.btn_tooltip1)
            // mRootView2 = getLayoutInflater().inflate(R.layout.login, container, false)


            // val btn = findViewById(R.id.btn_tooltip1) as ImageView
            dialogButton.setOnClickListener {
                // onBackPressed()
                mAlertDialog.dismiss()
                showGuidePopup2()
            }
            mAlertDialog.setOnCancelListener {  func ->  showGuidePopup2()}

            // mAlertDialog.getWindow().setGravity(Gravity.TOP)
            mAlertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    fun showGuidePopup2() {
        if (getActivity()!=null){
            val mDialogView = LayoutInflater.from(getActivity()).inflate(R.layout.tooltip_second, null)
            //AlertDialogBuilder
            val mBuilder = AlertDialog.Builder(getActivity())
                    .setView(mDialogView)
                    // .setTitle("Login Form")
            //show dialog
            val  mAlertDialog = mBuilder.show()

            val dialogButton:ImageView = mAlertDialog.findViewById(R.id.btn_tooltip2)
            dialogButton.setOnClickListener {
                mAlertDialog.dismiss()
                showConfigBottomSheetDialogFragment()
            }

            mAlertDialog.getWindow().setGravity(Gravity.TOP)
            mAlertDialog.getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

            mAlertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }    
    }

    fun scrollToFirst() {
        val isPageLoading = loadingView == null || loadingView!!.visibility == View.VISIBLE
        if (!isPageLoading) {
            loadingView!!.show()
            mWebview!!.loadUrl("javascript:scrollToFirst()")
            mActivityCallback!!.hideSystemUI()
            mIsBlockToggleMenu = true
        }
    }

    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    private fun initWebView() {

        val webViewLayout = mRootView!!.findViewById<FrameLayout>(R.id.webViewLayout)
        mWebview = webViewLayout.findViewById(R.id.folioWebView)
        mWebview!!.setParentFragment(this)
        webViewPager = webViewLayout.findViewById(R.id.webViewPager)

        if (activity is FolioActivityCallback)
            mWebview!!.setFolioActivityCallback((activity as FolioActivityCallback?)!!)

        setupScrollBar()
        mWebview!!.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val height = Math.floor((mWebview!!.contentHeight * mWebview!!.scale).toDouble()).toInt()
            val webViewHeight = mWebview!!.measuredHeight
            mScrollSeekbar!!.maximum = height - webViewHeight
        }

        mWebview!!.settings.javaScriptEnabled = true
        mWebview!!.isVerticalScrollBarEnabled = false
        mWebview!!.settings.allowFileAccess = true

        mWebview!!.isHorizontalScrollBarEnabled = false

        mWebview!!.addJavascriptInterface(this, "Highlight")
        mWebview!!.addJavascriptInterface(this, "FolioPageFragment")
        mWebview!!.addJavascriptInterface(webViewPager, "WebViewPager")
        mWebview!!.addJavascriptInterface(loadingView, "LoadingView")
        mWebview!!.addJavascriptInterface(mWebview, "FolioWebView")

        mWebview!!.setScrollListener(object : FolioWebView.ScrollListener {
            override fun onScrollChange(percent: Int) {
    
                var href = spineItem.href
                val regrex = Regex("[^0-9]")
                href = href?.replace(regrex, "")
                var chapEnable = mEnableChap?.replace(regrex, "")

                try {
                    if (Integer.valueOf(href) == Integer.valueOf(chapEnable)) {
                        isFinishChapToday = true
                    }

                    if (Integer.valueOf(href) > Integer.valueOf(chapEnable)) {
                        shouldBlock = true
                    } else {
                        shouldBlock = false
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "->====== ", e)
                }
                // mScrollSeekbar!!.setProgressAndThumb(percent)
                updatePagesLeftText(percent)
            }
        })

        mWebview!!.webViewClient = webViewClient
        mWebview!!.webChromeClient = webChromeClient

        mWebview!!.settings.defaultTextEncodingName = "utf-8"
        HtmlTask(this).execute(chapterUrl.toString())
    }

    private val webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {

            try {
                mWebview!!.loadUrl("javascript:checkCompatMode()")
                mWebview!!.loadUrl("javascript:alert(getReadingTime())")

                if (mActivityCallback!!.direction == Config.Direction.HORIZONTAL) {
                    isHorizontal = true
                    mWebview!!.loadUrl("javascript:initHorizontalDirection()")
                } else {
                    isHorizontal = false
                }

                view.loadUrl(
                    String.format(
                        getString(R.string.setmediaoverlaystyle),
                        HighlightImpl.HighlightStyle.classForStyle(
                            HighlightImpl.HighlightStyle.Normal
                        )
                    )
                )

                val rangy = HighlightUtil.generateRangyString(pageName)
                this@FolioPageFragment.rangy = rangy
                if (!rangy.isEmpty())
                    loadRangy(rangy)

                if (mIsPageReloaded) {

                    if (searchLocatorVisible != null) {
                        val callHighlightSearchLocator = String.format(
                            getString(R.string.callHighlightSearchLocator),
                            searchLocatorVisible?.locations?.cfi
                        )
                        mWebview!!.loadUrl(callHighlightSearchLocator)

                    } else if (isCurrentFragment) {
                        val cfi = lastReadLocator!!.locations.cfi
                        mWebview!!.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))

                    } else {
                        if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) {
                            // Scroll to last, the page before current page
                            mWebview!!.loadUrl("javascript:scrollToLast()")
                        } else {
                            // Make loading view invisible for all other fragments
                            loadingView!!.hide()
                        }
                    }

                    mIsPageReloaded = false

                } else if (!TextUtils.isEmpty(mAnchorId)) {
                    mWebview!!.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                    mAnchorId = null

                } else if (!TextUtils.isEmpty(highlightId)) {
                    mWebview!!.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
                    highlightId = null

                } else if (searchLocatorVisible != null) {
                    val callHighlightSearchLocator = String.format(
                        getString(R.string.callHighlightSearchLocator),
                        searchLocatorVisible?.locations?.cfi
                    )
                    mWebview!!.loadUrl(callHighlightSearchLocator)

                } else if (isCurrentFragment) {

                    if (mActivityCallback!!.getTooltipStep().count() < 2 && mStatusTooltip!!.count() > 0) {
                      showGuidePopup()
                      mActivityCallback!!.setTooltipStep()
                    }
            
                    // val a = mActivityCallback!!.getTooltipStatus()
                    // mActivityCallback!!.setTooltipStatus()

                    val readLocator: ReadLocator?
                    if (savedInstanceState == null) {
                        Log.v(LOG_TAG, "-> onPageFinished -> took from getEntryReadLocator")
                        readLocator = mActivityCallback!!.entryReadLocator
                    } else {
                        Log.v(LOG_TAG, "-> onPageFinished -> took from bundle")
                        readLocator = savedInstanceState!!.getParcelable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
                        savedInstanceState!!.remove(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
                    }

                    if (readLocator != null) {
                        val cfi = readLocator.locations.cfi
                        Log.v(LOG_TAG, "-> onPageFinished -> readLocator -> " + cfi!!)
                        mWebview!!.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))
                    } else {
                        loadingView!!.hide()
                    }

                } else {

                    if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) {
                        // Scroll to last, the page before current page
                        mWebview!!.loadUrl("javascript:scrollToLast()")
                    } else {
                        // Make loading view invisible for all other fragments
                        loadingView!!.hide()
                    }
                }
                if (!mIsBlockToggleMenu) {
                   mActivityCallback!!.hideSystemUI()
                } 
            } catch (e: Exception) {
                Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
            }

            mIsBlockToggleMenu = false
            // Log.v(LOG_TAG, "crollToFirst -> isPageLoading -> onPageFinished -> readLocator========> 1")

            // Log.d("length", mActivityCallback!!.currentChapterIndex.toString())

        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

            if (url.isEmpty())
                return true

            val urlOfEpub = mActivityCallback!!.goToChapter(url)
            if (!urlOfEpub) {
                // Otherwise, give the default behavior (open in browser)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }

            return true
        }

        // prevent favicon.ico to be loaded automatically
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            if (url.toLowerCase().contains("/favicon.ico")) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }

        // prevent favicon.ico to be loaded automatically
        @SuppressLint("NewApi")
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame
                && request.url.path != null
                && request.url.path!!.endsWith("/favicon.ico")
            ) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }
    }

    private val webChromeClient = object : WebChromeClient() {

        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
            super.onConsoleMessage(cm)
            val msg = cm.message() + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]"
            return FolioWebView.onWebViewConsoleMessage(cm, "WebViewConsole", msg)
        }

        override fun onProgressChanged(view: WebView, progress: Int) {}

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {

            // Check if this `if` block can be dropped?
            if (!this@FolioPageFragment.isVisible)
                return true

            if (TextUtils.isDigitsOnly(message)) {
                try {
                    mTotalMinutes = Integer.parseInt(message)
                } catch (e: NumberFormatException) {
                    mTotalMinutes = 0
                }

            } else {
                // to handle TTS playback when highlight is deleted.
                val p = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
                if (!p.matcher(message).matches() && message != "undefined" && isCurrentFragment) {
                    mediaController!!.speakAudio(message)
                }
            }

            result.confirm()
            return true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v(LOG_TAG, "-> onStop -> " + spineItem.href + " -> " + isCurrentFragment)

        mediaController!!.stop()
        //TODO save last media overlay item

       
    }

    fun load() {
        Log.v(LOG_TAG, "-> getLastReadLocator2 -> " + spineItem.href!!)
        try {
            synchronized(this) {
                mWebview!!.loadUrl(getString(R.string.callComputeLastReadCfi2))
                (this as java.lang.Object).wait(5000)
            }
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, "-> ", e)
        }

    }

    fun getLastReadLocator(): ReadLocator? {
        Log.v(LOG_TAG, "-> getLastReadLocator -> " + spineItem.href!!)
        try {
            synchronized(this) {
                mWebview!!.loadUrl(getString(R.string.callComputeLastReadCfi))
                (this as java.lang.Object).wait(5000)
            }
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, "-> ", e)
        }

        return lastReadLocator
    }

    @JavascriptInterface
    fun storeLastReadCfi(cfi: String, noCallback: Boolean) {

        synchronized(this) {
            var href = spineItem.href
            if (href == null) href = ""
            val created = Date().time
            val locations = Locations()
            locations.cfi = cfi
            lastReadLocator = ReadLocator(mBookId!!, href, created, locations)

            if (!noCallback) {
                val intent = Intent(FolioReader.ACTION_SAVE_READ_LOCATOR)
                intent.putExtra(FolioReader.EXTRA_READ_LOCATOR, lastReadLocator as Parcelable?)
                LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
            }

            (this as java.lang.Object).notify()
        }
    }

    @JavascriptInterface
    fun setHorizontalPageCount(horizontalPageCount: Int) {
        Log.v(
            LOG_TAG, "-> setHorizontalPageCount = " + horizontalPageCount
                    + " -> " + spineItem.href
        )
        mWebview!!.setHorizontalPageCount(horizontalPageCount)
    }

    fun goToEnableChap() {
        try {
            if (!isNavigate) {
                activity!!.runOnUiThread{ mActivityCallback!!.goToChapter(this.mEnableChap) }
                isNavigate = true
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "go enableChap error", e)
        }

        
    }

    fun hiddenSystemUI() {
        Log.v(
            LOG_TAG, "-> hiddeeeee"
        )
        mActivityCallback!!.hideSystemUI()
    }

    fun loadRangy(rangy: String) {
        mWebview!!.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.setHighlights('%s');}",
                rangy
            )
        )
    }

    private fun setupScrollBar() {
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, mScrollSeekbar!!.progressDrawable)
        val thumbDrawable = ContextCompat.getDrawable(activity!!, R.drawable.icons_sroll)
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, thumbDrawable!!)
        mScrollSeekbar!!.thumb = thumbDrawable
    }

    private fun initSeekbar() {
        mScrollSeekbar = mRootView!!.findViewById<View>(R.id.scrollSeekbar) as VerticalSeekbar
        mScrollSeekbar!!.progressDrawable
            .setColorFilter(
                resources
                    .getColor(R.color.default_theme_accent_color),
                PorterDuff.Mode.SRC_IN
            )
    }

    private fun updatePagesLeftTextBg() {
        if (mConfig!!.isNightMode) {
            mRootView!!.findViewById<View>(R.id.indicatorLayout)
                .setBackgroundColor(Color.parseColor("#131313"))
        } else {
            mRootView!!.findViewById<View>(R.id.indicatorLayout)
                .setBackgroundColor(Color.WHITE)
        }
    }

    private fun updatePagesLeftText(scrollY: Int) {
        try {
            val currentPage = (Math.ceil(scrollY.toDouble() / mWebview!!.webViewHeight) + 1).toInt()
            val totalPages = Math.ceil(mWebview!!.contentHeightVal.toDouble() / mWebview!!.webViewHeight).toInt()
            val pagesRemaining = totalPages - currentPage
            val pagesRemainingStrFormat = if (pagesRemaining > 1)
                getString(R.string.pages_left)
            else
                getString(R.string.page_left)
            val pagesRemainingStr = String.format(
                Locale.US,
                pagesRemainingStrFormat, pagesRemaining
            )

            
            
            val minutesRemaining = Math.ceil((pagesRemaining * mTotalMinutes).toDouble() / totalPages).toInt()
            val minutesRemainingStr: String

            if (minutesRemaining > 1) {
                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minutes_left),
                    minutesRemaining
                )
            } else if (minutesRemaining == 1) {

                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minute_left),
                    minutesRemaining
                )
            } else {
                minutesRemainingStr = getString(R.string.less_than_minute)
            }

            mMinutesLeftTextView!!.text = minutesRemainingStr
            mPagesLeftTextView!!.text = pagesRemainingStr
        } catch (exp: java.lang.ArithmeticException) {
            Log.e("divide error", exp.toString())
        } catch (exp: IllegalStateException) {
            Log.e("divide error", exp.toString())
        }

    }

    private fun initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(activity, R.anim.fadein)
        mFadeInAnimation!!.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                mScrollSeekbar!!.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation) {
                fadeOutSeekBarIfVisible()
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
        mFadeOutAnimation = AnimationUtils.loadAnimation(activity, R.anim.fadeout)
        mFadeOutAnimation!!.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {

            }

            override fun onAnimationEnd(animation: Animation) {
                mScrollSeekbar!!.visibility = View.INVISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
    }

    override fun fadeInSeekBarIfInvisible() {
        if (mScrollSeekbar!!.visibility == View.INVISIBLE || mScrollSeekbar!!.visibility == View.GONE) {
            mScrollSeekbar!!.startAnimation(mFadeInAnimation)
        }
    }

    fun fadeOutSeekBarIfVisible() {
        if (mScrollSeekbar!!.visibility == View.VISIBLE) {
            mScrollSeekbar!!.startAnimation(mFadeOutAnimation)
        }
    }

    override fun onDestroyView() {
        mFadeInAnimation!!.setAnimationListener(null)
        mFadeOutAnimation!!.setAnimationListener(null)
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    /**
     * If called, this method will occur after onStop() for applications targeting platforms
     * starting with Build.VERSION_CODES.P. For applications targeting earlier platform versions
     * this method will occur before onStop() and there are no guarantees about whether it will
     * occur before or after onPause()
     *
     * @see Activity.onSaveInstanceState
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.v(LOG_TAG, "-> onSaveInstanceState -> ${spineItem.href}")

        this.outState = outState
        outState.putParcelable(BUNDLE_SEARCH_LOCATOR, searchLocatorVisible)
    }

    fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean) {
        if (!isAlreadyCreated) {
            mWebview!!.loadUrl(
                String.format(
                    "javascript:if(typeof ssReader !== \"undefined\"){ssReader.highlightSelection('%s');}",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        } else {
            mWebview!!.loadUrl(
                String.format(
                    "javascript:setHighlightStyle('%s')",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        }
    }

    override fun resetCurrentIndex() {
        if (isCurrentFragment) {
            mWebview!!.loadUrl("javascript:rewindCurrentIndex()")
        }
    }

    @JavascriptInterface
    fun onReceiveHighlights(html: String?) {
        if (html != null) {
            rangy = HighlightUtil.createHighlightRangy(
                activity!!.applicationContext,
                html,
                mBookId,
                pageName,
                spineIndex,
                rangy
            )
        }
    }

    override fun highLightText(fragmentId: String) {
        mWebview!!.loadUrl(String.format(getString(R.string.audio_mark_id), fragmentId))
    }

    override fun highLightTTS() {
        mWebview!!.loadUrl("javascript:alert(getSentenceWithIndex('epub-media-overlay-playing'))")
    }

    @JavascriptInterface
    fun getUpdatedHighlightId(id: String?, style: String) {
        if (id != null) {
            val highlightImpl = HighLightTable.updateHighlightStyle(id, style)
            if (highlightImpl != null) {
                HighlightUtil.sendHighlightBroadcastEvent(
                    activity!!.applicationContext,
                    highlightImpl,
                    HighLight.HighLightAction.MODIFY
                )
            }
            val rangyString = HighlightUtil.generateRangyString(pageName)
            activity!!.runOnUiThread { loadRangy(rangyString) }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
         if (isCurrentFragment)
            getLastReadLocator()
            
        if (isCurrentFragment) {
            if (outState != null)
                outState!!.putSerializable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE, lastReadLocator)
            if (activity != null && !activity!!.isFinishing && lastReadLocator != null)
                mActivityCallback!!.storeLastReadLocator(lastReadLocator)
        }
        if (mWebview != null) mWebview!!.destroy()
    }

    override fun onError() {}

    fun scrollToHighlightId(highlightId: String) {
        this.highlightId = highlightId

        if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
            loadingView!!.show()
            mWebview!!.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
            this.highlightId = null
        }
    }

    fun highlightSearchLocator(searchLocator: SearchLocator) {
        Log.v(LOG_TAG, "-> highlightSearchLocator")
        this.searchLocatorVisible = searchLocator

        if (loadingView != null && loadingView!!.visibility != View.VISIBLE) {
            loadingView!!.show()
            val callHighlightSearchLocator = String.format(
                getString(R.string.callHighlightSearchLocator),
                searchLocatorVisible?.locations?.cfi
            )
            mWebview!!.loadUrl(callHighlightSearchLocator)
        }
    }

    fun clearSearchLocator() {
        Log.v(LOG_TAG, "-> clearSearchLocator -> " + spineItem.href!!)
        mWebview!!.loadUrl(getString(R.string.callClearSelection))
        searchLocatorVisible = null
    }

    fun showConfigBottomSheetDialogFragment() {
        ConfigBottomSheetDialogFragment().show(
             getActivity()!!.getSupportFragmentManager(),
            ConfigBottomSheetDialogFragment.LOG_TAG
        )
    }

}
