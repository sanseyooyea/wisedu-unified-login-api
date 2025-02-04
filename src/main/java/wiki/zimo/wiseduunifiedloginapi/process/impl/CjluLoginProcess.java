package wiki.zimo.wiseduunifiedloginapi.process.impl;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import wiki.zimo.wiseduunifiedloginapi.builder.CjluLoginEntityBuilder;
import wiki.zimo.wiseduunifiedloginapi.helper.AESHelper;
import wiki.zimo.wiseduunifiedloginapi.helper.TesseractOCRHelper;
import wiki.zimo.wiseduunifiedloginapi.process.OcrLoginProcess;
import wiki.zimo.wiseduunifiedloginapi.trust.HttpsUrlValidator;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 中国计量大学
 *
 * @author SanseYooyea
 */
public class CjluLoginProcess extends OcrLoginProcess {
    public CjluLoginProcess(String loginUrl, Map<String, String> params) {
        super(loginUrl, params, CjluLoginEntityBuilder.class);
    }

    @Override
    public Map<String, String> login() throws Exception {
        // 忽略证书错误
        HttpsUrlValidator.retrieveResponseFromServer(loginEntity.getLoginUrl());

        // 请求登陆页
        Connection con = Jsoup.connect(loginEntity.getLoginUrl())
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                .followRedirects(true);
        Connection.Response res = con.execute();

        // 解析登陆页
        Document doc = res.parse();

        // 全局cookie
        Map<String, String> cookies = res.cookies();

        // 获取登陆表单
        Element form = doc.getElementById("pwdLoginDiv");
        if (form == null) {
            throw new RuntimeException("网页中没有找到 pwdLoginDiv，请联系开发者！！！");
        }

        // 处理加密的盐
        Element saltElement = doc.getElementById("pwdEncryptSalt");

        String salt = null;
        if (saltElement != null) {
            salt = saltElement.val();
        }

        // 获取登陆表单里的输入
        Elements inputs = form.getElementsByTag("input");

        String username = params.get("username");
        String password = params.get("password");

        // 构造post请求参数
        Map<String, String> params = new HashMap<>();
        for (Element e : inputs) {
            // 填充用户名
            if ("username".equals(e.attr("name"))) {
                e.attr("value", username);
            }

            // 填充密码
            if ("password".equals(e.attr("name"))) {
                if (salt != null) {
                    e.attr("value", AESHelper.encryptAES(password, salt));
                } else {
                    e.attr("value", password);
                }
            }

            // 排除空值表单属性
            if (e.attr("name").length() == 0) {
                continue;
            }

            // 排除记住我
            if ("rememberMe".equals(e.attr("name"))) {
                continue;
            }

            params.put(e.attr("name"), e.attr("value"));
        }

        // 构造请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Cache-Control", "max-age=0");
        headers.put("Connection", "keep-alive");
        headers.put("Host", new URL(loginEntity.getLoginUrl()).getHost());
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");

        // 模拟登陆之前首先请求是否需要验证码接口
        doc = Jsoup.connect(loginEntity.getNeedcaptchaUrl() + "?username=" + username)
                .headers(headers)
                .cookies(cookies)
                .get();
        boolean isNeedCaptcha = doc.body().text().contains("true");

        if (isNeedCaptcha) {
            // 识别验证码后模拟登陆，最多尝试20次
            int time = TesseractOCRHelper.MAX_TRY_TIMES;
            while (time-- > 0) {
                String code = ocrCaptcha(cookies, headers, loginEntity.getCaptchaUrl(), 4);
                System.out.println("验证码识别结果：" + code);
                params.put("captcha", code);
                Map<String, String> cookies2 = casSendLoginData(loginEntity.getLoginUrl(), cookies, params);
                if (cookies2 != null) {
                    return cookies2;
                }
            }
            // 执行到这里就代表验证码识别尝试已经达到了最大的次数
            throw new RuntimeException("验证码识别错误，请重试");
        } else {
            // 直接模拟登陆
            return casSendLoginData(loginEntity.getLoginUrl(), cookies, params);
        }
    }

    @Override
    protected Map<String, String> casSendLoginData(String login_url, Map<String, String> cookies, Map<String, String> params) throws Exception {
        Connection con = Jsoup.connect(login_url)
                .header("Origin", "https://authserver.cjlu.edu.cn")
                .header("Referer", "https://authserver.cjlu.edu.cn/authserver/login");
        ;
//        System.out.println(login_url);
//        System.out.println(params);
        Connection.Response login = null;
        try {
            login = con.ignoreContentType(true)
                    .followRedirects(false)
                    .method(Connection.Method.POST)
                    .data(params)
                    .cookies(cookies)
                    .execute();
        } catch (Exception e) {
            System.out.println("=======》");
            e.printStackTrace();
        }

//        System.out.println(params);
//        System.out.println(login.statusCode());
        if (login.statusCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
            // 重定向代表登陆成功
            // 第一次尝试自动更新cookie
            String location = null;
            try {
                cookies.putAll(login.cookies());
                // 拿到重定向的地址
                location = login.header("location");
//                System.out.println(location);
                con = Jsoup.connect(location)
                        .ignoreContentType(true)
                        .followRedirects(true)
                        .method(Connection.Method.POST)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                        .cookies(cookies);
                // 请求，再次更新cookie
                login = con.execute();
                cookies.putAll(login.cookies());
            } catch (HttpStatusException e) {
//                e.printStackTrace();
                // 第一次自动更新cookie，失败了，携带已获取到的cookie再次尝试/portal/login接口
                location = location.substring(0, location.lastIndexOf('?'));
//                System.out.println(location);
                con = Jsoup.connect(location)
                        .ignoreContentType(true)
                        .followRedirects(true)
                        .method(Connection.Method.GET)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                        .cookies(cookies);
                login = con.execute();
                cookies.putAll(login.cookies());
            }
            // 只有登陆成功才返回cookie
            return cookies;
        } else if (login.statusCode() == HttpURLConnection.HTTP_OK) {
            // 登陆失败
            Document doc = login.parse();
            Element msg = doc.getElementById("pwdLoginDiv").getElementById("pwdFromId").getElementById("formErrorTip");
            throw new RuntimeException(msg.text());
        } else {
            // 服务器可能出错
            throw new RuntimeException("教务系统服务器可能出错了，Http状态码是：" + login.statusCode());
        }
    }
}
