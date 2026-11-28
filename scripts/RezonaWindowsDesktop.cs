using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace RezonaDesktop
{
    static class Program
    {
        private const string DesktopUrl = "https://rezona-music.vercel.app/desktop.html";

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // Try launching in native App Window mode via Microsoft Edge / Chrome WebView2 shell first
            if (TryLaunchNativeAppWindow())
            {
                return;
            }

            // Fallback: open in default browser
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = DesktopUrl,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show("Gagal membuka Rezona Windows Studio: " + ex.Message, "Rezona Desktop", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static bool TryLaunchNativeAppWindow()
        {
            // Check if local desktop.html exists next to the executable for instant offline/local launch
            string localHtml = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "web", "public", "desktop.html");
            string targetUrl = File.Exists(localHtml) ? new Uri(localHtml).AbsoluteUri : DesktopUrl;

            string[] browserCandidates = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Google\Chrome\Application\chrome.exe")
            };

            foreach (string exe in browserCandidates)
            {
                if (File.Exists(exe))
                {
                    try
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = exe,
                            Arguments = "--app=\"" + targetUrl + "\" --window-size=1360,860",
                            UseShellExecute = false
                        });
                        return true;
                    }
                    catch
                    {
                    }
                }
            }
            return false;
        }
    }
}
