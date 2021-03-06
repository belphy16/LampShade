package com.kuxhausen.huemore;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.util.ArrayMap;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.kuxhausen.huemore.alarm.AlarmsListFragment;
import com.kuxhausen.huemore.editmood.EditMoodFragment;
import com.kuxhausen.huemore.net.Connection;
import com.kuxhausen.huemore.net.ConnectionListFragment;
import com.kuxhausen.huemore.net.NetworkBulb;
import com.kuxhausen.huemore.net.NetworkBulb.ConnectivityState;
import com.kuxhausen.huemore.nfc.NfcWriterFragment;
import com.kuxhausen.huemore.persistence.Definitions.InternalArguments;
import com.kuxhausen.huemore.persistence.Definitions.PreferenceKeys;
import com.kuxhausen.huemore.persistence.PreferenceInitializer;
import com.kuxhausen.huemore.state.Group;

import java.util.ArrayList;

public class NavigationDrawerActivity extends NetworkManagedActivity implements
                                                                     OnBackStackChangedListener {

  private Toolbar mToolbar;
  private DrawerLayout mDrawerLayout;
  private View mDrawerView;
  private ListView mDrawerList, mNotificationList;
  private ActionBarDrawerToggle mDrawerToggle;
  private NotificationRowAdapter mNotificationAdapter;

  private CharSequence mTitle;
  private ArrayList<String> mDrawerTitles;
  private ArrayMap<String, Integer> mDrawerTitlePositions;

  public int mSelectedItemPosition;
  public Tag myTag;

  /**
   * For tracking drill-down navigation. Number of up-navigation-actions away from the top. At the
   * top level, value is 0 and hamburger/nav drawer shown instead of up arrow.
   */
  private int mLayersDeep = 0;

  private SharedPreferences mSettings;

  private Bundle mResumeBundle;

  private String TITLE_BULB_FRAG, TITLE_GROUP_FRAG, TITLE_CONNECTIONS_FRAG, TITLE_ALARM_FRAG,
      TITLE_NFC_FRAG, TITLE_SETTINGS_FRAG, TITLE_HELP_FRAG;

  private ArrayList<SelectableList> mSelectableLists;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Helpers.applyLocalizationPreference(this);

    setContentView(R.layout.activity_navigation_drawer);

    generateDrawerTitles();

    mSelectableLists = new ArrayList<>();

    mToolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
    setSupportActionBar(mToolbar);

    mTitle = getTitle();

    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    mDrawerList = (ListView) findViewById(R.id.drawer_list);

    // set a custom shadow that overlays the main content when the drawer opens
    mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
    mDrawerLayout.setStatusBarBackgroundColor(getResources().getColor(R.color.day_primary_dark));

    // set up the drawer's list view with items and click listener
    mDrawerList
        .setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, mDrawerTitles));
    mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

    mDrawerView = findViewById(R.id.left_drawer);

    Helpers.fixBackgroundRepeat(findViewById(R.id.lampshade_banner));

    mNotificationList = (ListView) findViewById(R.id.notification_list);

    // enable ActionBar app icon to behave as action to toggle nav drawer
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);

    // ActionBarDrawerToggle ties together the the proper interactions
    // between the sliding drawer and the action bar app icon
    mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
                                              mDrawerLayout, /* DrawerLayout object */
                                              mToolbar,
                                              R.string.drawer_open, /* "open drawer" description for accessibility */
                                              R.string.drawer_close /* "close drawer" description for accessibility */
    ) {
      public void onDrawerClosed(View view) {
        supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
      }

      public void onDrawerOpened(View drawerView) {
        supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
      }
    };
    mDrawerLayout.setDrawerListener(mDrawerToggle);

    mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mLayersDeep != 0) {
          onBackPressed();
        } else if (mDrawerLayout.isDrawerOpen(mDrawerView)) {
          mDrawerLayout.closeDrawer(mDrawerView);
        } else {
          mDrawerLayout.openDrawer(mDrawerView);
        }

      }
    });
    mDrawerToggle.syncState();

    Bundle b = getIntent().getExtras();
    mSettings = PreferenceManager.getDefaultSharedPreferences(this);

    getSupportFragmentManager().addOnBackStackChangedListener(this);

    PreferenceInitializer.initializedPreferencesAndShowDialogs(this);
  }

  private void generateDrawerTitles() {
    TITLE_BULB_FRAG = getString(R.string.nav_drawer_bulbs);
    TITLE_GROUP_FRAG = getString(R.string.nav_drawer_groups);
    TITLE_CONNECTIONS_FRAG = getString(R.string.nav_drawer_connections);
    TITLE_ALARM_FRAG = getString(R.string.nav_drawer_alarms);
    TITLE_NFC_FRAG = getString(R.string.nav_drawer_nfc);
    TITLE_SETTINGS_FRAG = getString(R.string.nav_drawer_settings);
    TITLE_HELP_FRAG = getString(R.string.nav_drawer_help);

    /** Generate the drawer items list for this device **/
    mDrawerTitles = new ArrayList<>();
    mDrawerTitles.add(TITLE_BULB_FRAG);
    mDrawerTitles.add(TITLE_GROUP_FRAG);
    mDrawerTitles.add(TITLE_CONNECTIONS_FRAG);
    //Alarms only supported on 3.1+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      mDrawerTitles.add(TITLE_ALARM_FRAG);
    }
    //Only show NFC option on supported devices
    //if (NfcAdapter.getDefaultAdapter(this) == null) {
    mDrawerTitles.add(TITLE_NFC_FRAG);
    //}
    mDrawerTitles.add(TITLE_SETTINGS_FRAG);
    mDrawerTitles.add(TITLE_HELP_FRAG);

    mDrawerTitlePositions = new ArrayMap<String, Integer>();
    for (int i = 0; i < mDrawerTitles.size(); i++) {
      mDrawerTitlePositions.put(mDrawerTitles.get(i), i);
    }
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();

    if (mLayersDeep > 0) {
      mLayersDeep--;
    }
  }

  public void onResume() {
    super.onResume();

    mResumeBundle = getIntent().getExtras();

    Uri data = getIntent().getData();
    if (data != null && data.getHost().equals("lampshade.io")) {
      SharedMoodDialog dialog = new SharedMoodDialog();
      Bundle extras = new Bundle();
      extras.putString(InternalArguments.ENCODED_MOOD, data.getQuery());
      dialog.setArguments(extras);
      dialog.show(getSupportFragmentManager(), InternalArguments.FRAG_MANAGER_DIALOG_TAG);
    }
  }

  /**
   * onCreate & onResumeFragments are the  only lifecycle methods where fragment transactions can
   * commit without risking state loss http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
   */
  public void onResumeFragments() {
    super.onResumeFragments();

    Bundle b = mResumeBundle;
    if (b != null && b.containsKey(InternalArguments.NAV_DRAWER_TITLE)) {
      String desiredTitle = b.getString(InternalArguments.NAV_DRAWER_TITLE);
      if (mDrawerTitlePositions.containsKey(desiredTitle)) {
        selectItem(mDrawerTitlePositions.get(desiredTitle), b);
      }
      b.remove(InternalArguments.NAV_DRAWER_TITLE);
    } else {
      if (mSettings.getBoolean(PreferenceKeys.DEFAULT_TO_GROUPS, false)) {
        selectItem(mDrawerTitlePositions.get(TITLE_GROUP_FRAG), b);
      } else {
        selectItem(mDrawerTitlePositions.get(TITLE_BULB_FRAG), b);
      }
    }

    if (b != null && b.getBoolean(InternalArguments.FLAG_SHOW_NAV_DRAWER)) {
      mDrawerLayout.openDrawer(Gravity.LEFT);
      b.remove(InternalArguments.FLAG_SHOW_NAV_DRAWER);
    }

    mResumeBundle = null;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.navigation_drawer, menu);
    return super.onCreateOptionsMenu(menu);
  }

  /* Called whenever we call invalidateOptionsMenu() */
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // If the nav drawer is open, hide action items related to the content view
    boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerView);

    boolean hasPendingOrSuccessfulConnections = false;
    if (this.boundToService()) {
      ArrayList<Connection> cons = getService().getDeviceManager().getConnections();
      for (Connection c : cons) {
        for (NetworkBulb b : c.getBulbs()) {
          if (b.getConnectivityState() == ConnectivityState.Connected
              || b.getConnectivityState() == ConnectivityState.Unknown) {
            hasPendingOrSuccessfulConnections = true;
          }
        }
      }
    }
    //hide connectivity error icon on connections page or no error
    if (mSelectedItemPosition == mDrawerTitlePositions.get(TITLE_CONNECTIONS_FRAG)
        || hasPendingOrSuccessfulConnections) {
      MenuItem unlocksItem = menu.findItem(R.id.action_connectivity_error);
      unlocksItem.setEnabled(false);
      unlocksItem.setVisible(false);
    } else {
      MenuItem unlocksItem = menu.findItem(R.id.action_connectivity_error);
      unlocksItem.setEnabled(true);
      unlocksItem.setVisible(true);
    }

    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // The action bar home/up action should open or close the drawer.
    // ActionBarDrawerToggle will take care of this.
    if (mDrawerToggle.onOptionsItemSelected(item)) {
      return true;
    }
    // Handle action buttons
    switch (item.getItemId()) {
      case R.id.action_connectivity_error:
        showConnectivity();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /* The click listner for ListView in the navigation drawer */
  private class DrawerItemClickListener implements ListView.OnItemClickListener {

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      selectItem(position, null);
    }
  }

  private void selectItem(int position, Bundle b) {
    if (b == null) {
      b = new Bundle();
    }

    // record the groupbulb tab state and pass in bundle to main
    if (position == mDrawerTitlePositions.get(TITLE_GROUP_FRAG)) {
      b.putInt(InternalArguments.GROUPBULB_TAB, GroupBulbPagerAdapter.GROUP_LOCATION);
      saveTab(GroupBulbPagerAdapter.GROUP_LOCATION);
    } else if (position == mDrawerTitlePositions.get(TITLE_BULB_FRAG)) {
      b.putInt(InternalArguments.GROUPBULB_TAB, GroupBulbPagerAdapter.BULB_LOCATION);
      saveTab(GroupBulbPagerAdapter.BULB_LOCATION);
    }

    // this allows Bulb & Group to show up twice in NavBar but share fragment
    int actualPosition = position;
    if (actualPosition == mDrawerTitlePositions.get(TITLE_GROUP_FRAG)) {
      actualPosition = mDrawerTitlePositions.get(TITLE_BULB_FRAG);
    }

    mSelectedItemPosition = position;
    cleanUpActionBar();

    Fragment
        selectedFrag =
        getSupportFragmentManager().findFragmentByTag(mDrawerTitles.get(actualPosition));

    if (selectedFrag == null) {
      if (mDrawerTitles.get(actualPosition).equals(TITLE_BULB_FRAG)) {
        selectedFrag = new MainFragment();
      } else if (mDrawerTitles.get(actualPosition).equals(TITLE_CONNECTIONS_FRAG)) {
        selectedFrag = new ConnectionListFragment();
      } else if (mDrawerTitles.get(actualPosition).equals(TITLE_SETTINGS_FRAG)) {
        selectedFrag = new SettingsFragment();
      } else if (mDrawerTitles.get(actualPosition).equals(TITLE_HELP_FRAG)) {
        selectedFrag = new HelpFragment();
      } else if (mDrawerTitles.get(actualPosition).equals(TITLE_ALARM_FRAG)) {
        selectedFrag = new AlarmsListFragment();
      } else if (mDrawerTitles.get(actualPosition).equals(TITLE_NFC_FRAG)) {
        selectedFrag = new NfcWriterFragment();
      }
      selectedFrag.setArguments(b);
    } else {
      // if can't pass groupbulb tab data in bundle because frag already exists, pass directly
      if (actualPosition == mDrawerTitlePositions.get(TITLE_BULB_FRAG)) {
        ((MainFragment) selectedFrag).setTab(b.getInt(InternalArguments.GROUPBULB_TAB));
      }
    }

    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    fragmentManager.beginTransaction()
        .replace(R.id.content_frame, selectedFrag, mDrawerTitles.get(actualPosition)).commit();

    mLayersDeep = 0;
    this.supportInvalidateOptionsMenu();

    // update selected item and title, then close the drawer
    mDrawerList.setItemChecked(position, true);
    setTitle(mDrawerTitles.get(position));
    mDrawerLayout.closeDrawer(mDrawerView);
  }

  @Override
  public void onServiceConnected() {
    mNotificationAdapter = new NotificationRowAdapter(this, getService().getMoodPlayer());
    mNotificationList.setAdapter(mNotificationAdapter);
  }

  @Override
  public void setTitle(CharSequence title) {
    mTitle = title;
    getSupportActionBar().setTitle(mTitle);
  }

  /**
   * When using the ActionBarDrawerToggle, you must call it during onPostCreate() and
   * onConfigurationChanged()...
   */

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Sync the toggle state after onRestoreInstanceState has occurred.
    mDrawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Pass any configuration change to the drawer toggals
    mDrawerToggle.onConfigurationChanged(newConfig);
  }

  private void cleanUpActionBar() {
    getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
  }

  @Override
  public void onBackStackChanged() {
    cleanUpActionBar();
    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
      mDrawerToggle.setDrawerIndicatorEnabled(false);
      mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    } else {
      mDrawerToggle.setDrawerIndicatorEnabled(true);
      mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }
  }

  private void onDrillDownNavigation() {
    mLayersDeep++;
    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  public void showHelp(String pageName) {
    Bundle b = new Bundle();
    b.putString(InternalArguments.HELP_PAGE, pageName);
    selectItem(mDrawerTitlePositions.get(TITLE_HELP_FRAG), b);

    // TODO find a way of showing help page without clearning back stack yet still enabling nav
    // drawer
    /*
     * HelpActivity frag = new HelpActivity(); if(pageName!=null){ Bundle b = new Bundle();
     * b.putString(InternalArguments.HELP_PAGE, pageName); frag.setArguments(b); } FragmentManager
     * fragmentManager = getSupportFragmentManager();
     * fragmentManager.beginTransaction().addToBackStack(null).replace(R.id.content_frame,
     * frag).commit();
     */
  }

  public void showConnectivity() {
    selectItem(mDrawerTitlePositions.get(TITLE_CONNECTIONS_FRAG), null);

    // TODO find a way of showing connectivity page without clearning back stack yet still enabling
    // nav drawer
  }

  public void showEditMood(String moodName) {
    EditMoodFragment frag = new EditMoodFragment();
    if (moodName != null) {
      Bundle b = new Bundle();
      b.putString(InternalArguments.MOOD_NAME, moodName);
      frag.setArguments(b);
    }
    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.beginTransaction().addToBackStack("mood").replace(R.id.content_frame, frag)
        .commit();

    onDrillDownNavigation();
  }

  public void setGroup(Group g, SelectableList from) {
    super.setGroup(g);

    for (SelectableList selectable : mSelectableLists) {
      if (selectable != from) {
        selectable.invalidateSelection();
      }
    }

    if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
        < Configuration.SCREENLAYOUT_SIZE_LARGE & boundToService()) {
      SecondaryFragment drillDownFrag = new SecondaryFragment();

      FragmentManager fragmentManager = getSupportFragmentManager();
      fragmentManager.beginTransaction().addToBackStack("group")
          .replace(R.id.content_frame, drillDownFrag).commit();

      onDrillDownNavigation();
    }
  }

  @Override
  public void setGroup(Group g) {
    setGroup(g, null);
  }

  public void trackSelectableList(SelectableList list) {
    mSelectableLists.add(list);
  }

  public void forgetSelectableList(SelectableList list) {
    mSelectableLists.remove(list);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
    if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
      myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

      Toast.makeText(this, this.getString(R.string.nfc_tag_detected), Toast.LENGTH_SHORT).show();

    }
  }

  public void markSelected(int pagerPosition) {
    int drawerPosition;
    if (pagerPosition == GroupBulbPagerAdapter.BULB_LOCATION) {
      drawerPosition = mDrawerTitlePositions.get(TITLE_BULB_FRAG);
    } else {
      drawerPosition = mDrawerTitlePositions.get(TITLE_GROUP_FRAG);
    }
    mDrawerList.setItemChecked(drawerPosition, true);
    setTitle(mDrawerTitles.get(drawerPosition));
    saveTab(pagerPosition);
  }

  public void saveTab(int position) {
    Editor edit = mSettings.edit();
    switch (position) {
      case GroupBulbPagerAdapter.BULB_LOCATION:
        edit.putBoolean(PreferenceKeys.DEFAULT_TO_GROUPS, false);
        break;
      case GroupBulbPagerAdapter.GROUP_LOCATION:
        edit.putBoolean(PreferenceKeys.DEFAULT_TO_GROUPS, true);
        break;
    }
    edit.commit();

  }

  @Override
  public void onConnectionStatusChanged() {
    this.supportInvalidateOptionsMenu();
  }

  @Override
  protected void onDestroy() {
    mNotificationAdapter.onDestroy();
    super.onDestroy();
  }

}
