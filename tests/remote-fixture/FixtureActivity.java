package org.neotv.remote.fixture;
public final class FixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView label=new android.widget.TextView(this);label.setText("Neo remote installation test — disposable fixture");label.setTextColor(0xff8feaff);label.setBackgroundColor(0xff071326);label.setTextSize(24);label.setGravity(android.view.Gravity.CENTER);setContentView(label);}
}
