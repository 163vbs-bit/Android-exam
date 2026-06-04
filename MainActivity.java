package com.example.todolist.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.todolist.R;
import com.example.todolist.model.Task;
import com.example.todolist.notifications.NotificationHelper;
import com.example.todolist.viewmodel.TaskViewModel;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Главный экран приложения со списком задач.
 */
public class MainActivity extends AppCompatActivity implements TaskAdapter.TaskActionListener {
    private static final String PREFS_NAME = "todo_settings";
    private static final String KEY_DARK_THEME = "dark_theme";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;
    private TextView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        NotificationHelper.createNotificationChannel(this);
        requestNotificationPermission();
        setupRecyclerView();
        setupViewModel();
        setupToolbar();
        setupTabs();
        setupFab();
    }

    /**
     * Обновляет задачу при изменении флажка выполнения.
     */
    @Override
    public void onTaskCompletionChanged(Task task, boolean completed) {
        task.setCompleted(completed);
        taskViewModel.update(task);
        if (completed) {
            NotificationHelper.cancelReminder(this, task);
        } else if (task.getReminderAtMillis() > 0) {
            NotificationHelper.scheduleReminder(this, task);
        }
    }

    /**
     * Удаляет задачу после завершения анимации исчезновения карточки.
     */
    @Override
    public void onTaskDeleteRequested(Task task) {
        NotificationHelper.cancelReminder(this, task);
        taskViewModel.delete(task);
    }

    /**
     * Обрабатывает результат запроса разрешения на показ уведомлений.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setupToolbar() {
        CheckBox darkThemeToggle = findViewById(R.id.darkThemeToggle);
        darkThemeToggle.setChecked(isDarkThemeEnabled());
        darkThemeToggle.setOnCheckedChangeListener((buttonView, enabled) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_DARK_THEME, enabled)
                    .apply();
            AppCompatDelegate.setDefaultNightMode(enabled
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });
    }

    private void setupTabs() {
        FrameLayout tabsContainer = findViewById(R.id.taskTabsContainer);
        TabLayout tabLayout = new TabLayout(this);
        tabLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.filter_all));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.filter_active));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.filter_completed));
        tabsContainer.removeAllViews();
        tabsContainer.addView(tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                taskViewModel.setFilter(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupRecyclerView() {
        emptyState = findViewById(R.id.emptyState);
        RecyclerView recyclerView = findViewById(R.id.tasksRecycler);
        taskAdapter = new TaskAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(taskAdapter);
    }

    private void setupFab() {
        findViewById(R.id.addTaskFab).setOnClickListener(v -> showTaskBottomSheet());
    }

    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        taskViewModel.getFilteredTasks().observe(this, this::renderTasks);
    }

    private void renderTasks(List<Task> tasks) {
        taskAdapter.submitList(tasks);
        emptyState.setVisibility(tasks == null || tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showTaskBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        FrameLayout parent = new FrameLayout(this);
        View content = getLayoutInflater().inflate(R.layout.bottom_sheet_task, parent, false);
        dialog.setContentView(content);

        TextInputEditText titleInput = content.findViewById(R.id.titleInput);
        TextInputEditText descriptionInput = content.findViewById(R.id.descriptionInput);
        Spinner prioritySpinner = content.findViewById(R.id.prioritySpinner);
        CheckBox reminderCheckBox = content.findViewById(R.id.reminderCheckBox);
        TextInputLayout reminderInputLayout = content.findViewById(R.id.reminderInputLayout);
        TextInputEditText reminderInput = content.findViewById(R.id.reminderInput);
        MaterialButton saveButton = content.findViewById(R.id.saveTaskButton);
        Calendar reminderCalendar = Calendar.getInstance();
        reminderCalendar.add(Calendar.HOUR_OF_DAY, 1);

        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.priority_high),
                        getString(R.string.priority_medium),
                        getString(R.string.priority_low)
                }
        );
        prioritySpinner.setAdapter(priorityAdapter);
        prioritySpinner.setSelection(Task.PRIORITY_MEDIUM);
        updateReminderInput(reminderInput, reminderCalendar);
        reminderInputLayout.setEnabled(false);
        reminderInput.setOnClickListener(v -> pickReminderDateTime(reminderCalendar, reminderInput));
        reminderCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> reminderInputLayout.setEnabled(isChecked));
        saveButton.setOnClickListener(v -> saveTask(dialog, titleInput, descriptionInput, prioritySpinner,
                reminderCheckBox, reminderCalendar));
        dialog.show();
    }

    private void pickReminderDateTime(Calendar calendar, TextInputEditText reminderInput) {
        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                updateReminderInput(reminderInput, calendar);
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timeDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dateDialog.show();
    }

    private void saveTask(BottomSheetDialog dialog, TextInputEditText titleInput, TextInputEditText descriptionInput,
                          Spinner prioritySpinner, CheckBox reminderCheckBox, Calendar reminderCalendar) {
        String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
        String description = descriptionInput.getText() == null ? "" : descriptionInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError(getString(R.string.task_title));
            return;
        }
        long reminderAt = reminderCheckBox.isChecked() ? reminderCalendar.getTimeInMillis() : 0L;
        Task task = new Task(title, description, prioritySpinner.getSelectedItemPosition(), reminderAt, false,
                System.currentTimeMillis());
        taskViewModel.insert(task, insertedTask -> {
            if (insertedTask.getReminderAtMillis() > 0) {
                NotificationHelper.scheduleReminder(this, insertedTask);
            }
        });
        dialog.dismiss();
    }

    private void updateReminderInput(TextInputEditText reminderInput, Calendar calendar) {
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        reminderInput.setText(dateFormat.format(new Date(calendar.getTimeInMillis())));
    }

    private void requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(isDarkThemeEnabled()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private boolean isDarkThemeEnabled() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getBoolean(KEY_DARK_THEME, false);
    }
}
