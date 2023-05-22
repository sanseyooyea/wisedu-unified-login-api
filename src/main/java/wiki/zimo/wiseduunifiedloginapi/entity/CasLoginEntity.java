package wiki.zimo.wiseduunifiedloginapi.entity;

public class CasLoginEntity extends BaseLoginEntity {

    public CasLoginEntity(String loginUrl, String needcaptchaUrl, String captchaUrl) {
        super(loginUrl, needcaptchaUrl, captchaUrl);
    }

    @Override
    public String toString() {
        return "CasLoginEntity{" +
                "loginUrl='" + getLoginUrl() + '\'' +
                ", needcaptchaUrl='" + getNeedcaptchaUrl() + '\'' +
                ", captchaUrl='" + getCaptchaUrl() + '\'' +
                '}';
    }
}
