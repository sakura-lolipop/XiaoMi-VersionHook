package com.lolipop.versionhook;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 极简配置：一个输入框设定期望版本。
 * 作用域在 META-INF/xposed/scope.list 预置，管理器里可增删。
 */
public class MainActivity extends Activity {

    private static final String CONF_GROUP = MainModule.CONF_GROUP;
    private static final String KEY_FAKE = MainModule.KEY_FAKE;

    private EditText inVer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("作用域内的 App 读到的系统版本");
        title.setPadding(0, 24, 0, 8);
        root.addView(title);

        inVer = new EditText(this);
        inVer.setHint("OS4.0.11.0.XPNCNXM 或任意自定义串");
        root.addView(inVer);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        root.addView(save);

        TextView hint = new TextView(this);
        hint.setText("保存后强停再打开目标 App 生效。留空保存 = 默认伪装（OS4.0.11.0.XPNCNXM）。
"
                + "不伪装直接在 LSPosed 里停用本模块。");
        hint.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 40, 0, 0);
        root.addView(hint, lp);

        setContentView(root);
    }

    private void save() {
        String ver = inVer.getText().toString().trim();
        getSharedPreferences("config", MODE_PRIVATE)
                .edit().putString(KEY_FAKE, ver).apply();
        toast(ver.isEmpty() ? "已保存：默认伪装" : "已保存：" + ver);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
