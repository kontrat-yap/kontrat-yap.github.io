package com.kontratyap.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only fetches the public HTML template. Form contents stay on the device. */
public final class MainActivity extends Activity {
    private static final String URL = "https://kontrat-yap.github.io/";
    private static final String CACHE_FILE = "last-valid-page.html";
    private static final int MAX_HTML = 2 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView view;
    private TextView loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(15, 110, 86));
        getWindow().setNavigationBarColor(Color.rgb(20, 20, 18));
        FrameLayout layout = new FrameLayout(this);
        view = new WebView(this);
        view.setBackgroundColor(Color.rgb(246, 245, 241));
        layout.addView(view, new FrameLayout.LayoutParams(-1,-1));
        loading = new TextView(this);
        loading.setText("Kontrat Yap\nGüncel sürüm kontrol ediliyor…");
        loading.setTextSize(17);
        loading.setTextColor(Color.rgb(15,110,86));
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(Color.rgb(246,245,241));
        layout.addView(loading, new FrameLayout.LayoutParams(-1,-1));
        setContentView(layout);

        WebSettings web = view.getSettings();
        web.setJavaScriptEnabled(true);
        web.setDomStorageEnabled(false);
        web.setAllowFileAccess(false);
        web.setAllowContentAccess(false);
        web.setAllowFileAccessFromFileURLs(false);
        web.setAllowUniversalAccessFromFileURLs(false);
        // Prevent JavaScript in a downloaded HTML update from making network requests.
        web.setBlockNetworkLoads(true);
        web.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setSupportZoom(false);

        view.addJavascriptInterface(new NativePrinting(), "NativePrinting");
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return true;
            }
            @Override public void onPageFinished(WebView webView, String url) {
                webView.evaluateJavascript("(function(){" +
                    "window.print=function(){NativePrinting.savePdf();};" +
                    "var b=document.getElementById('pdf');" +
                    "if(b)b.onclick=function(){NativePrinting.savePdf();};" +
                    "})();",null);
            }
        });
        // New HTML on each launch; on failure use last cached version, then bundled version.
        worker.execute(() -> {
            String html = null;
            try { html = downloadHtml(); writeCache(html); } catch(Exception ignored) { }
            if(html == null) {
                try { html = readCache(); } catch(Exception ignored) { }
            }
            if(html == null) {
                try(InputStream in = getAssets().open("index.html")) {
                    html = readAll(in);
                } catch(Exception ignored) { }
            }
            final String output = html;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || view == null) return;
                if(output == null) loading.setText("HTML dosyası açılamadı.");
                else {
                    view.loadDataWithBaseURL(URL,output,"text/html","UTF-8",null);
                    loading.setVisibility(View.GONE);
                }
            });
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[8192];
        int n;
        while((n=stream.read(bytes))!=-1) {
            if(buffer.size()+n>MAX_HTML) throw new IllegalStateException("HTML too large");
            buffer.write(bytes,0,n);
        }
        return buffer.toString("UTF-8");
    }

    private static boolean valid(String html) {
        if(html == null) return false;
        String lower=html.toLowerCase(Locale.ROOT);
        return lower.contains("<html") && lower.contains("</html>") &&
            html.contains("Kira Belgesi Hazırla");
    }

    private String downloadHtml() throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(URL).openConnection();
        try {
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(6000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control","no-cache");
            connection.setRequestProperty("Accept","text/html");
            connection.setRequestProperty("Accept-Encoding","identity");
            if(connection.getResponseCode()!=200) throw new IllegalStateException("HTTP error");
            URL finalUrl=connection.getURL();
            if(!"https".equals(finalUrl.getProtocol()) ||
               !"kontrat-yap.github.io".equalsIgnoreCase(finalUrl.getHost()))
                throw new SecurityException("Unexpected redirect");
            String content;
            try(InputStream stream=connection.getInputStream()) {content=readAll(stream);}
            if(!valid(content)) throw new IllegalStateException("Bad HTML");
            return content;
        } finally {
            connection.disconnect();
        }
    }

    private void writeCache(String html) throws Exception {
        AtomicFile cache=new AtomicFile(new File(getFilesDir(),CACHE_FILE));
        FileOutputStream out=null;
        try {
            out=cache.startWrite();
            out.write(html.getBytes(StandardCharsets.UTF_8));
            cache.finishWrite(out);
        } catch(Exception e) {
            if(out!=null) cache.failWrite(out);
            throw e;
        }
    }

    private String readCache() throws Exception {
        AtomicFile cache=new AtomicFile(new File(getFilesDir(),CACHE_FILE));
        String html;
        try(InputStream in=cache.openRead()) { html=readAll(in); }
        return valid(html)?html:null;
    }

    private final class NativePrinting {
        @JavascriptInterface public void savePdf() {
            runOnUiThread(() -> {
                if(view==null || isFinishing()) return;
                try {
                    PrintManager p=(PrintManager)getSystemService(Context.PRINT_SERVICE);
                    PrintAttributes attributes=new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build();
                    p.print("Kontrat Yap - Belge",
                        view.createPrintDocumentAdapter("Kontrat Yap - Belge"),attributes);
                } catch(Exception e) {
                    Toast.makeText(MainActivity.this,"PDF kaydedilemedi: "+e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if(view!=null) {
            view.removeJavascriptInterface("NativePrinting");
            view.destroy();
            view=null;
        }
        super.onDestroy();
    }
}
