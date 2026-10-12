package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends Activity {
    private XposedService service;
    private Switch logSwitch;
    private Boolean pendingEnable;
    private boolean updating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F6F7F9"));
        scrollView.setFitsSystemWindows(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp2px(20);
        layout.setPadding(pad, dp2px(24), pad, pad);

        // Header Title
        TextView title = new TextView(this);
        title.setText("小蚕净化");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#1A1A1A"));
        layout.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("小蚕霸王餐 Xposed / LSPosed 净化模块");
        subTitle.setTextSize(13);
        subTitle.setTextColor(Color.parseColor("#888888"));
        subTitle.setPadding(0, dp2px(4), 0, dp2px(16));
        layout.addView(subTitle);

        // Status Card
        LinearLayout statusCard = createCard();
        TextView statusBadge = new TextView(this);
        statusBadge.setText("● 模块已就绪");
        statusBadge.setTextSize(15);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setTextColor(Color.parseColor("#07C160"));
        statusCard.addView(statusBadge);

        TextView statusDesc = new TextView(this);
        statusDesc.setText("目标应用: com.realtech.xiaocan (小蚕霸王餐)\n在 LSPosed 勾选小蚕霸王餐后重启小蚕即可生效。");
        statusDesc.setTextSize(13);
        statusDesc.setTextColor(Color.parseColor("#555555"));
        statusDesc.setPadding(0, dp2px(6), 0, 0);
        statusCard.addView(statusDesc);
        layout.addView(statusCard);

        // Settings Card: logging toggle
        LinearLayout settingsCard = createCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout rowText = new LinearLayout(this);
        rowText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rowText.setLayoutParams(textLp);

        TextView logTitle = new TextView(this);
        logTitle.setText("运行日志");
        logTitle.setTextSize(14);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(Color.parseColor("#222222"));
        rowText.addView(logTitle);

        TextView logDesc = new TextView(this);
        logDesc.setText("默认关闭。开启后写入 Android/data/com.realtech.xiaocan/files/XiaoCanPurify.log，重启小蚕生效。");
        logDesc.setTextSize(12);
        logDesc.setTextColor(Color.parseColor("#888888"));
        logDesc.setPadding(0, dp2px(2), 0, 0);
        rowText.addView(logDesc);

        logSwitch = new Switch(this);
        row.addView(rowText);
        row.addView(logSwitch);
        settingsCard.addView(row);
        layout.addView(settingsCard);

        logSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updating) {
                return;
            }
            pendingEnable = checked;
            applyLogEnabled();
        });
        bindLogService();

        // Section Title
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("已启用的功能");
        sectionTitle.setTextSize(16);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setTextColor(Color.parseColor("#333333"));
        sectionTitle.setPadding(0, dp2px(16), 0, dp2px(8));
        layout.addView(sectionTitle);

        // Feature 1
        layout.addView(createFeatureCard("去除所有广告",
                "• 瞬间跳过冷启动开屏广告\n" +
                "• 彻底阻止切后台返回的冷热启动开屏广告\n" +
                "• 拦截穿山甲、优量汇、快手、Sigmob、Baidu、WindMill 等全屏/激励广告\n" +
                "• 屏蔽首页与到店页信息流/卡片广告\n" +
                "• 禁用后台静默隐蔽任务引擎 (SilentTaskEngine)"));

        // Feature 2
        layout.addView(createFeatureCard("去除各类弹窗",
                "• 拦截首页营销弹窗、首单全返活动弹窗\n" +
                "• 拦截抖音商城红包弹窗、蛋糕/周年庆弹窗\n" +
                "• 屏蔽应用强制检查升级弹窗\n" +
                "• 屏蔽元宝升级提示弹窗、开学季及分享弹窗\n" +
                "• 净化全局弹窗队列与 Compose 浮动弹窗"));

        // Feature 3
        layout.addView(createFeatureCard("精简底部导航栏",
                "• 仅保留「首页」、「订单」、「我」\n" +
                "• 彻底移除「会员」和「福利/赚钱」两个冗余标签\n" +
                "• 自动适配 3 个 Tab 的点击与页面切换联动\n" +
                "• 消除点击跳转会员与登录拦截干扰"));

        // Feature 4
        layout.addView(createFeatureCard("网络层广告过滤",
                "• 拦截 placement 广告配置与数据请求 (/g/pa)\n" +
                "• 阻断 AdProLink、Sigmob、CSJ、GDT 等广告网络流量\n" +
                "• 拦截埋点与追踪数据上报 (Burying / SensorsData)"));

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void bindLogService() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                MainActivity.this.service = service;
                runOnUiThread(MainActivity.this::syncLogEnabled);
            }

            @Override
            public void onServiceDied(XposedService service) {
                if (MainActivity.this.service == service) {
                    MainActivity.this.service = null;
                }
            }
        });
    }

    private void syncLogEnabled() {
        Boolean pending = pendingEnable;
        if (pending != null) {
            pendingEnable = null;
            applyLogEnabled();
            return;
        }
        if (logSwitch == null || service == null) {
            return;
        }
        boolean enabled = false;
        try {
            enabled = service.getRemotePreferences(ModuleLog.PREFS_GROUP)
                    .getBoolean(ModuleLog.KEY_ENABLED, false);
        } catch (Throwable ignored) {
        }
        updating = true;
        logSwitch.setChecked(enabled);
        updating = false;
    }

    private void applyLogEnabled() {
        if (pendingEnable == null || service == null) {
            return;
        }
        try {
            service.getRemotePreferences(ModuleLog.PREFS_GROUP)
                    .edit().putBoolean(ModuleLog.KEY_ENABLED, pendingEnable).apply();
        } catch (Throwable ignored) {
        }
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        int p = dp2px(14);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp2px(12));
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout createFeatureCard(String title, String desc) {
        LinearLayout card = createCard();
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#222222"));
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(Color.parseColor("#666666"));
        d.setLineSpacing(dp2px(2), 1.15f);
        d.setPadding(0, dp2px(6), 0, 0);
        card.addView(d);
        return card;
    }

    private int dp2px(float dp) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
