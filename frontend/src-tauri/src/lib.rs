// `Manager` 仅在桌面端使用（移动端没有 devtools），按 cfg 守卫避免未使用导入警告
#[cfg(desktop)]
use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|_app| {
            #[cfg(debug_assertions)]
            {
                // open_devtools 仅桌面端可用，移动端无此 API，必须守卫
                #[cfg(desktop)]
                if let Some(window) = _app.get_webview_window("main") {
                    window.open_devtools();
                }
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
