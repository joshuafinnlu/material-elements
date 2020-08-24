

package com.zeoflow.material.elements.tabs;

import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.lang.ref.WeakReference;


public final class TabLayoutMediator {
  @NonNull private final TabLayout tabLayout;
  @NonNull private final ViewPager2 viewPager;
  private final boolean autoRefresh;
  private final boolean smoothScroll;
  private final TabConfigurationStrategy tabConfigurationStrategy;
  @Nullable private RecyclerView.Adapter<?> adapter;
  private boolean attached;

  @Nullable private TabLayoutOnPageChangeCallback onPageChangeCallback;
  @Nullable private TabLayout.OnTabSelectedListener onTabSelectedListener;
  @Nullable private RecyclerView.AdapterDataObserver pagerAdapterObserver;

  
  public interface TabConfigurationStrategy {
    
    void onConfigureTab(@NonNull TabLayout.Tab tab, int position);
  }

  public TabLayoutMediator(
      @NonNull TabLayout tabLayout,
      @NonNull ViewPager2 viewPager,
      @NonNull TabConfigurationStrategy tabConfigurationStrategy) {
    this(tabLayout, viewPager,  true, tabConfigurationStrategy);
  }

  public TabLayoutMediator(
      @NonNull TabLayout tabLayout,
      @NonNull ViewPager2 viewPager,
      boolean autoRefresh,
      @NonNull TabConfigurationStrategy tabConfigurationStrategy) {
    this(tabLayout, viewPager, autoRefresh,  true, tabConfigurationStrategy);
  }

  public TabLayoutMediator(
      @NonNull TabLayout tabLayout,
      @NonNull ViewPager2 viewPager,
      boolean autoRefresh,
      boolean smoothScroll,
      @NonNull TabConfigurationStrategy tabConfigurationStrategy) {
    this.tabLayout = tabLayout;
    this.viewPager = viewPager;
    this.autoRefresh = autoRefresh;
    this.smoothScroll = smoothScroll;
    this.tabConfigurationStrategy = tabConfigurationStrategy;
  }

  
  public void attach() {
    if (attached) {
      throw new IllegalStateException("TabLayoutMediator is already attached");
    }
    adapter = viewPager.getAdapter();
    if (adapter == null) {
      throw new IllegalStateException(
          "TabLayoutMediator attached before ViewPager2 has an " + "adapter");
    }
    attached = true;

    
    onPageChangeCallback = new TabLayoutOnPageChangeCallback(tabLayout);
    viewPager.registerOnPageChangeCallback(onPageChangeCallback);

    
    onTabSelectedListener = new ViewPagerOnTabSelectedListener(viewPager, smoothScroll);
    tabLayout.addOnTabSelectedListener(onTabSelectedListener);

    
    
    if (autoRefresh) {
      
      pagerAdapterObserver = new PagerAdapterObserver();
      adapter.registerAdapterDataObserver(pagerAdapterObserver);
    }

    populateTabsFromPagerAdapter();

    
    tabLayout.setScrollPosition(viewPager.getCurrentItem(), 0f, true);
  }

  
  public void detach() {
    if (autoRefresh && adapter != null) {
      adapter.unregisterAdapterDataObserver(pagerAdapterObserver);
      pagerAdapterObserver = null;
    }
    tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
    viewPager.unregisterOnPageChangeCallback(onPageChangeCallback);
    onTabSelectedListener = null;
    onPageChangeCallback = null;
    adapter = null;
    attached = false;
  }

  @SuppressWarnings("WeakerAccess")
  void populateTabsFromPagerAdapter() {
    tabLayout.removeAllTabs();

    if (adapter != null) {
      int adapterCount = adapter.getItemCount();
      for (int i = 0; i < adapterCount; i++) {
        TabLayout.Tab tab = tabLayout.newTab();
        tabConfigurationStrategy.onConfigureTab(tab, i);
        tabLayout.addTab(tab, false);
      }
      
      if (adapterCount > 0) {
        int lastItem = tabLayout.getTabCount() - 1;
        int currItem = Math.min(viewPager.getCurrentItem(), lastItem);
        if (currItem != tabLayout.getSelectedTabPosition()) {
          tabLayout.selectTab(tabLayout.getTabAt(currItem));
        }
      }
    }
  }

  
  private static class TabLayoutOnPageChangeCallback extends ViewPager2.OnPageChangeCallback {
    @NonNull private final WeakReference<TabLayout> tabLayoutRef;
    private int previousScrollState;
    private int scrollState;

    TabLayoutOnPageChangeCallback(TabLayout tabLayout) {
      tabLayoutRef = new WeakReference<>(tabLayout);
      reset();
    }

    @Override
    public void onPageScrollStateChanged(final int state) {
      previousScrollState = scrollState;
      scrollState = state;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      TabLayout tabLayout = tabLayoutRef.get();
      if (tabLayout != null) {
        
        
        boolean updateText =
            scrollState != SCROLL_STATE_SETTLING || previousScrollState == SCROLL_STATE_DRAGGING;
        
        
        
        boolean updateIndicator =
            !(scrollState == SCROLL_STATE_SETTLING && previousScrollState == SCROLL_STATE_IDLE);
        tabLayout.setScrollPosition(position, positionOffset, updateText, updateIndicator);
      }
    }

    @Override
    public void onPageSelected(final int position) {
      TabLayout tabLayout = tabLayoutRef.get();
      if (tabLayout != null
          && tabLayout.getSelectedTabPosition() != position
          && position < tabLayout.getTabCount()) {
        
        
        boolean updateIndicator =
            scrollState == SCROLL_STATE_IDLE
                || (scrollState == SCROLL_STATE_SETTLING
                    && previousScrollState == SCROLL_STATE_IDLE);
        tabLayout.selectTab(tabLayout.getTabAt(position), updateIndicator);
      }
    }

    void reset() {
      previousScrollState = scrollState = SCROLL_STATE_IDLE;
    }
  }

  
  private static class ViewPagerOnTabSelectedListener implements TabLayout.OnTabSelectedListener {
    private final ViewPager2 viewPager;
    private final boolean smoothScroll;

    ViewPagerOnTabSelectedListener(ViewPager2 viewPager, boolean smoothScroll) {
      this.viewPager = viewPager;
      this.smoothScroll = smoothScroll;
    }

    @Override
    public void onTabSelected(@NonNull TabLayout.Tab tab) {
      viewPager.setCurrentItem(tab.getPosition(), smoothScroll);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
      
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
      
    }
  }

  private class PagerAdapterObserver extends RecyclerView.AdapterDataObserver {
    PagerAdapterObserver() {}

    @Override
    public void onChanged() {
      populateTabsFromPagerAdapter();
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
      populateTabsFromPagerAdapter();
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
      populateTabsFromPagerAdapter();
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      populateTabsFromPagerAdapter();
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      populateTabsFromPagerAdapter();
    }

    @Override
    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
      populateTabsFromPagerAdapter();
    }
  }
}
