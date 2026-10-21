package com.telegram.sessionmanager.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.telegram.sessionmanager.R;
import com.telegram.sessionmanager.util.TimeHelper;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface SessionClickListener {
        void onSessionClick(TdApi.Session session);
        void onTerminateClick(TdApi.Session session);
    }

    private final List<TdApi.Session> sessionList = new ArrayList<>();
    private final SessionClickListener listener;

    public SessionAdapter(SessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(TdApi.Session[] sessions) {
        sessionList.clear();
        if (sessions != null) {
            for (TdApi.Session s : sessions) {
                // Filter out current session since it is shown pinned in the hero card
                if (!s.isCurrent) {
                    sessionList.add(s);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getOtherSessionsCount() {
        return sessionList.size();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        TdApi.Session session = sessionList.get(position);
        holder.bind(session, listener);
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardSession;
        private final ImageView ivDeviceIcon;
        private final TextView tvDeviceModel;
        private final TextView tvAppName;
        private final TextView tvLocationIp;
        private final TextView tvLastActive;
        private final TextView badgeOfficial;
        private final TextView badgePending;
        private final MaterialButton btnTerminate;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSession = itemView.findViewById(R.id.cardSession);
            ivDeviceIcon = itemView.findViewById(R.id.ivDeviceIcon);
            tvDeviceModel = itemView.findViewById(R.id.tvDeviceModel);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvLocationIp = itemView.findViewById(R.id.tvLocationIp);
            tvLastActive = itemView.findViewById(R.id.tvLastActive);
            badgeOfficial = itemView.findViewById(R.id.badgeOfficial);
            badgePending = itemView.findViewById(R.id.badgePending);
            btnTerminate = itemView.findViewById(R.id.btnTerminate);
        }

        public void bind(TdApi.Session session, SessionClickListener listener) {
            // Device Model & Platform
            String deviceText = session.deviceModel;
            if (session.platform != null && !session.platform.isEmpty() &&
                !deviceText.toLowerCase().contains(session.platform.toLowerCase())) {
                deviceText += " (" + session.platform + ")";
            }
            tvDeviceModel.setText(deviceText);

            // Application Name & Version
            String appText = session.applicationName;
            if (session.applicationVersion != null && !session.applicationVersion.isEmpty()) {
                appText += " " + session.applicationVersion;
            }
            tvAppName.setText(appText);

            // Device Icon based on platform / model
            int iconRes = getDeviceIcon(session.platform, session.deviceModel, session.applicationName);
            ivDeviceIcon.setImageResource(iconRes);

            // Location & IP Address
            StringBuilder locIp = new StringBuilder();
            if (session.ipAddress != null && !session.ipAddress.isEmpty()) {
                locIp.append(session.ipAddress);
            }
            if (session.location != null && !session.location.isEmpty()) {
                if (locIp.length() > 0) locIp.append(" • ");
                locIp.append(session.location);
            }
            if (locIp.length() == 0) {
                locIp.append("Location unavailable");
            }
            tvLocationIp.setText(locIp.toString());

            // Last Active
            tvLastActive.setText(TimeHelper.formatLastActive(session.lastActiveDate));

            // Official App Badge
            badgeOfficial.setVisibility(session.isOfficialApplication ? View.VISIBLE : View.GONE);

            // Pending Badges
            if (session.isPasswordPending) {
                badgePending.setVisibility(View.VISIBLE);
                badgePending.setText("2FA Pending");
            } else if (session.isUnconfirmed) {
                badgePending.setVisibility(View.VISIBLE);
                badgePending.setText("Unconfirmed");
            } else {
                badgePending.setVisibility(View.GONE);
            }

            // Click Handlers
            cardSession.setOnClickListener(v -> {
                if (listener != null) listener.onSessionClick(session);
            });

            btnTerminate.setOnClickListener(v -> {
                if (listener != null) listener.onTerminateClick(session);
            });
        }

        private static int getDeviceIcon(String platform, String deviceModel, String appName) {
            String combined = (platform + " " + deviceModel + " " + appName).toLowerCase();
            if (combined.contains("android") || combined.contains("iphone") || combined.contains("ios") || combined.contains("phone")) {
                return R.drawable.ic_device_phone;
            } else if (combined.contains("windows") || combined.contains("macos") || combined.contains("mac") ||
                       combined.contains("linux") || combined.contains("desktop") || combined.contains("pc")) {
                return R.drawable.ic_device_laptop;
            } else if (combined.contains("web") || combined.contains("chrome") || combined.contains("firefox") ||
                       combined.contains("safari") || combined.contains("edge") || combined.contains("opera")) {
                return R.drawable.ic_device_web;
            }
            return R.drawable.ic_device_other;
        }
    }
}
