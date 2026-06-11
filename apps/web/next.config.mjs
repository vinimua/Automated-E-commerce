/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "**.cos.ap-singapore.myqcloud.com",
      },
    ],
  },
};

export default nextConfig;
