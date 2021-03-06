package com.zncm.library.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.afollestad.materialdialogs.MaterialDialog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.malinskiy.materialicons.Iconify;
import com.miguelcatalan.materialsearchview.MaterialSearchView;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;
import com.thin.downloadmanager.DefaultRetryPolicy;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListener;
import com.thin.downloadmanager.ThinDownloadManager;
import com.zncm.library.R;
import com.zncm.library.data.Constant;
import com.zncm.library.data.EnumData;
import com.zncm.library.data.Fields;
import com.zncm.library.data.Items;
import com.zncm.library.data.Lib;
import com.zncm.library.data.MyApplication;
import com.zncm.library.data.RefreshEvent;
import com.zncm.library.data.VideoInfo;
import com.zncm.library.utils.DbHelper;
import com.zncm.library.utils.Dbutils;
import com.zncm.library.utils.FileMiniUtil;
import com.zncm.library.utils.MyPath;
import com.zncm.library.utils.MySp;
import com.zncm.library.utils.NotiHelper;
import com.zncm.library.utils.XUtil;
import com.zncm.library.utils.htmlbot.contentextractor.ContentExtractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import de.greenrobot.event.EventBus;

import static com.zncm.library.utils.ApiUrils.myLib;


public class WebViewActivity extends BaseAc {

    private WebView mWebView;
    private String url;
    private Activity ctx;
    static boolean isImport = false;
    MaterialSearchView searchView;

    Set<String> urlSet = new HashSet<>();
    ArrayList<String> urls = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("");
        initView();
        initData();

    }

    @Override
    protected int setCV() {
        return R.layout.activity_webview;
    }

    private void initView() {
        ctx = this;
        url = getIntent().getExtras().getString("url");
        isImport = getIntent().getExtras().getBoolean("isImport", false);


        mWebView = (WebView) findViewById(R.id.mWebView);
        mWebView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, final String url) {
                if (!XUtil.notEmptyOrNull(url)) {
                    return true;
                }


                Tasks.executeInBackground(ctx, new BackgroundWork<Boolean>() {
                    @Override
                    public Boolean doInBackground() throws Exception {
                        try {
                            Document doc;
                            doc = Jsoup.connect(url).timeout(5000).get();
                            Elements srcLinks = doc.select("img[src$=.jpg]");
                            for (Element link : srcLinks) {
                                String imagesPath = link.attr("src");
                                XUtil.debug("imagesPath==>" + imagesPath);
                                ShareAc.initSave(Constant.SYS_PICS, url, imagesPath);
                            }
//                            String html = doc.html();
//                            String content = new TextExtractor().extract(html);
                            return true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return false;
                    }
                }, new Completion<Boolean>() {
                    @Override
                    public void onSuccess(Context context, Boolean result) {

                    }

                    @Override
                    public void onError(Context context, Exception e) {
                    }
                });

//                mWebView.loadUrl(url);

                WebViewActivity.this.url = url;

                initData();

                return true;
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                getSupportActionBar().setTitle(view.getTitle());

                if (progress == 100 && XUtil.notEmptyOrNull(view.getTitle())) {
                    XUtil.debug("progress == 100" + url + " " + view.getTitle());

                }
            }
        });
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {

                new MaterialDialog.Builder(ctx)
                        .title("文件")
                        .content("确认下载" + url)
                        .positiveText("下载")
                        .negativeText("取消")
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);
                                XUtil.tShort("文件正在下载，请稍后...");
                                downloadFile(WebViewActivity.this, url, isImport);
                            }
                        }).show();


            }
        });
        searchView = (MaterialSearchView) ctx.findViewById(R.id.search_view);
        searchView.setHint("搜索");
        searchView.setOnQueryTextListener(new MaterialSearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                XUtil.debug("query:" + query);
                if (XUtil.notEmptyOrNull(query)) {
                    if (query.startsWith("http") || query.startsWith("www")) {
                        mWebView.loadUrl(query);
                    } else {
                        mWebView.loadUrl(Constant.NET_BAIDU + query);
                    }
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        searchView.setOnSearchViewListener(new MaterialSearchView.SearchViewListener() {
            @Override
            public void onSearchViewShown() {
            }

            @Override
            public void onSearchViewClosed() {
            }
        });


    }


    public static class MyTask extends AsyncTask<String, Void, Void> {

        protected Void doInBackground(String... params) {
            try {
                String content = ContentExtractor.getContentByURL(params[0]);
                ShareAc.initSave(Constant.SYS_NET_TEXT, content, params[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

        }
    }


    public static void downloadFile(final Context ctx, final String url, final boolean isImport) {
        ThinDownloadManager downloadManager = new ThinDownloadManager(4);
        String fileEnd = "_";
        if (isImport) {
            fileEnd = ".csv";
        } else if (url.contains(".")) {
            fileEnd = url.substring(url.lastIndexOf("."));
            if (XUtil.notEmptyOrNull(fileEnd)) {
                if (fileEnd.contains("?")) {
                    fileEnd = fileEnd.substring(0, fileEnd.indexOf("?"));
                }
                if (fileEnd.length() > 10) {
                    fileEnd = fileEnd.substring(0, 10);
                }
            }
        }
        final String newFile = MyPath.getPathDownload() + "/" + System.currentTimeMillis() + fileEnd;
        Uri downloadUri = Uri.parse(url);
        Uri destinationUri = Uri.parse(newFile);
        DownloadRequest downloadRequest = new DownloadRequest(downloadUri)
                .setRetryPolicy(new DefaultRetryPolicy())
                .setDestinationURI(destinationUri).setPriority(DownloadRequest.Priority.HIGH)
                .setDownloadListener(new DownloadStatusListener() {
                    @Override
                    public void onDownloadComplete(int id) {
                        XUtil.debug("onDownloadComplete" + id);
                        if (isImport) {
                            if (newFile.contains("csv") || newFile.contains("txt")) {
                                if (url.equals(Constant.LOCLIB_NET)) {
                                    DbHelper.importLocCsv(ctx, new File(newFile));
                                } else {
                                    SettingAc.getData(ctx, newFile);
                                }
                            } else {
                                XUtil.tShort("格式不支持");
                            }
                        }

//                        XUtil.debug("下载完毕");
                        XUtil.tLong("文件下载完毕：" + newFile);

                        Intent openUnKnowFileIntent = FileMiniUtil.getUnKnowIntent(newFile);
                        NotiHelper.noti("下载完成", newFile, "文件下载完毕~~~", openUnKnowFileIntent, false, new Random().nextInt());

//                        XUtil.openFile(ctx, newFile);

                        if (XUtil.notEmptyOrNull(newFile)) {
                            ShareAc.initSave(Constant.SYS_FILE_DOWNLOAD, newFile, url);
                        }

                    }

                    @Override
                    public void onDownloadFailed(int id, int errorCode, String errorMessage) {
                        XUtil.debug("onDownloadComplete" + id + " " + errorCode + " " + errorMessage);
                    }


                    @Override
                    public void onProgress(int id, long totalBytes, long downloadedBytes, int progress) {
                        XUtil.debug("onDownloadComplete" + id + " " + progress);
                    }
                });
        downloadManager.add(downloadRequest);
    }


    private void initData() {
        if (!XUtil.notEmptyOrNull(url)) {
            return;
        }
        if (urlSet.add(url)) {
            urls.add(url);
        }
        mWebView.loadUrl(url);
        ShareAc.initSave(Constant.SYS_NET_HISTORY, url, url);
//        MyTask myTask = new MyTask();
//        myTask.execute(url, url);
    }


    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return;
        }
        super.onBackPressed();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item == null || item.getTitle() == null) {
            return false;
        }


        switch (item.getItemId()) {
            case 1:
                XUtil.sendTo(ctx, url);
                break;
            case 2:
                ShareAc.initSave(Constant.SYS_COLLECT, url, mWebView.getTitle());


                XUtil.tShort("已收藏网址");
                break;
            case 3:
                XUtil.copyText(ctx, url);
                XUtil.tShort("已复制");
                break;
            case 4:
                if (XUtil.notEmptyOrNull(url) && !url.startsWith("file:///")) {
                    if (item.getTitle().equals("在浏览器中打开")) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(i);
                        } catch (Exception e) {

                        }
                    }
                }
                break;
            case 5:
                mWebView.reload();
                break;
            case 6:
                ArrayList<String> strs = new ArrayList<>();
                if (XUtil.notEmptyOrNull(url)) {
                    strs.add(mWebView.getTitle());
                    strs.add(url);
                }
                ShareAc.initSaveList(Constant.SYS_SC, Constant.SYS_SC_MK, strs, Lib.libType.sys.value());
                XUtil.tShort("已添加收藏");
                break;

            case 7:
                searchView.showSearch(true);
                break;
            case 8:
                if (XUtil.listNotNull(urls)) {
                    url = urls.get(urls.size() - 1);
                }
                initData();
                break;

            case 9:
                Lib data = Dbutils.getLibSys(Constant.SYS_NET_HISTORY);
                if (data != null) {
                    Intent intent = new Intent(ctx, ItemsAc.class);
                    intent.putExtra(Constant.KEY_PARAM_DATA, data);
                    startActivity(intent);
                }
                break;


            case 10:
                ShareAc.initLibNet(url, "li");
                XUtil.tShort("已添加到网络库！");
                break;


            case 11:
                geUrlHtml(url);
                break;
            case 12:
                ShareAc.initLibRss(url, url);
                XUtil.tShort("已添加RSS源！");
                break;

            case 13:
                Intent intent = new Intent(ctx, PlayActivity.class);
                intent.putExtra(Constant.KEY_PARAM_DATA, new VideoInfo(url, url));
                startActivity(intent);
                break;

            case 14:
                ShareAc.initLibApi(url, "results");
                XUtil.tShort("已添加到API库！");
                break;

            default:
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    private void geUrlHtml(final String url) {
        try {

            //XUtil.tShort(Constant.SYS_BAIDU_BK + "-正在下载，第" + page + "页");
            Tasks.executeInBackground(MyApplication.getInstance().ctx, new BackgroundWork<String>() {
                @Override
                public String doInBackground() throws Exception {
                    String htmlStr = "";
                    try {
                        if (XUtil.isEmptyOrNull(url)) {
                            return htmlStr;
                        }
                        Document doc = Jsoup.connect(url).timeout(3000).get();
                        htmlStr = doc.html();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return htmlStr;
                }
            }, new Completion<String>() {
                @Override
                public void onSuccess(Context context, String result) {
                    if (XUtil.notEmptyOrNull(result)) {
                        Intent newIntent = new Intent(context, ShowInfoActivity.class);
                        newIntent.putExtra("show", result);
                        context.startActivity(newIntent);
                    }
                }

                @Override
                public void onError(Context context, Exception e) {
                }
            });


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu sub = menu.addSubMenu("");
        sub.setIcon(XUtil.initIconWhite(Iconify.IconValue.md_more_vert));
        sub.add(0, 1, 0, "分享");
        sub.add(0, 2, 0, "收藏网页");
        sub.add(0, 3, 0, "复制链接");
        sub.add(0, 4, 0, "在浏览器中打开");
        sub.add(0, 5, 0, "刷新");
        sub.add(0, 6, 0, "收藏");
        sub.add(0, 7, 0, "百度一下");
        sub.add(0, 8, 0, "上一页");
        sub.add(0, 9, 0, "历史记录");
        sub.add(0, 10, 0, "添加到网络库");
        sub.add(0, 11, 0, "html源码");
        sub.add(0, 12, 0, "添加RSS源");
        sub.add(0, 13, 0, "播放页面视频");
        sub.add(0, 14, 0, "添加到API库");
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

}
