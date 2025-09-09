package com.example.lumosonic.ui.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.lumosonic.databinding.FragmentUserBinding;
import com.example.lumosonic.ipc.MusicService;
import com.example.lumosonic.ui.home.HomeViewModel;

public class UserFragment extends Fragment {

    private FragmentUserBinding binding;
    private UserViewModel userViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        binding = FragmentUserBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // 初始化界面
        return root;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewListeners();
    }

    private void initViewListeners() {
       binding.optionLocal.setOnClickListener(v -> startActivity(new Intent(getActivity(), LocalMusicActivity.class)));
       binding.optionFavorites.setOnClickListener(v -> startActivity(new Intent(getActivity(), FavoriteMusicActivity.class)));
       binding.optionExit.setOnClickListener(v -> {
           Intent intent = new Intent(getActivity(), MusicService.class);
           intent.setAction(MusicService.ACTION_SCAN_MUSIC);
           getActivity().stopService(intent);
           getActivity().finish();
       });


    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
