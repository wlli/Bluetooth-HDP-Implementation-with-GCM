package com.google.android.gcm.demo.app;

import static com.google.android.gcm.demo.app.CommonUtilities.SENDER_ID;

import com.google.android.gcm.GCMRegistrar;

import android.os.Bundle;
import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class AndroiddTabLayoutActivity extends TabActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_androidd_tab_layout);
 
        TabHost tabHost = getTabHost();
 
        // Tab for Photos
        TabSpec bpSpec = tabHost.newTabSpec("BP");
        // setting Title and Icon for the Tab
        bpSpec.setIndicator("Blood Pressure", getResources().getDrawable(R.drawable.icon_bp_tab));
        Intent photosIntent = new Intent(this, BluetoothHDPActivity.class);
        bpSpec.setContent(photosIntent);
 
        // Tab for Songs
        TabSpec gcmSpec = tabHost.newTabSpec("GCM");
        gcmSpec.setIndicator("Message", getResources().getDrawable(R.drawable.icon_gcm_tab));
        Intent songsIntent = new Intent(this, DemoActivity.class);
        gcmSpec.setContent(songsIntent);
 
        
 
        // Adding all TabSpec to TabHost
        tabHost.addTab(bpSpec); // Adding photos tab
        tabHost.addTab(gcmSpec); // Adding songs tab
    }
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_androidd_tab_layout, menu);
        return true;
    }
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
       
        /**/
        case R.id.options_register:
            GCMRegistrar.register(this, SENDER_ID);
            return true;
        case R.id.options_unregister:
            GCMRegistrar.unregister(this);
            return true;
        default:
            return super.onOptionsItemSelected(item);
		}
	}
}
