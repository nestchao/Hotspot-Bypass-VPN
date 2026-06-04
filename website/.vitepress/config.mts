import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Hotspot Bypass VPN',
  description: 'Bypass carrier hotspot restrictions with Wi-Fi Direct + SOCKS5 proxy',

  base: '/Hotspot-Bypass-VPN-Unlimited-Hotspot/',

  head: [
    ['link', { rel: 'icon', href: '/images/ic_launcher.webp' }],
  ],

  cleanUrls: true,
  ignoreDeadLinks: true,

  themeConfig: {
    logo: '/images/app_logo.png',

    socialLinks: [
      { icon: 'github', link: 'https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot' },
    ],

    i18nRouting: true,
  },

  locales: {
    root: {
      label: 'English',
      lang: 'en',
      link: '/',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Features', link: '/features' },
          { text: 'Guide', items: [
            { text: 'Android App', link: '/guide/android' },
            { text: 'Windows Client', link: '/guide/windows' },
            { text: 'Non-App Devices', link: '/guide/non-app-devices' },
          ]},
          { text: 'FAQ', link: '/faq' },
          { text: 'Download', link: '/download' },
        ],
        sidebar: {
          '/guide/': [
            { text: 'Android App', link: '/guide/android' },
            { text: 'Windows Client', link: '/guide/windows' },
            { text: 'Non-App Devices', link: '/guide/non-app-devices' },
          ],
        },
        footer: {
          message: 'Released under the MIT License. Copyright © 2026 nestchao',
        },
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '首页', link: '/zh/' },
          { text: '功能介绍', link: '/zh/features' },
          { text: '使用指南', items: [
            { text: 'Android App', link: '/zh/guide/android' },
            { text: 'Windows 客户端', link: '/zh/guide/windows' },
            { text: '非App设备', link: '/zh/guide/non-app-devices' },
          ]},
          { text: '常见问题', link: '/zh/faq' },
          { text: '下载', link: '/zh/download' },
        ],
        sidebar: {
          '/zh/guide/': [
            { text: 'Android App', link: '/zh/guide/android' },
            { text: 'Windows 客户端', link: '/zh/guide/windows' },
            { text: '非App设备', link: '/zh/guide/non-app-devices' },
          ],
        },
        footer: {
          message: '基于 MIT 许可发布。Copyright © 2026 nestchao',
        },
      },
    },
  },
})
