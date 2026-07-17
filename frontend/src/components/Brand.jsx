// App wordmark with the squirrel logo. The SVG lives in /public and is served at the root.
export default function Brand({ title = 'Job Application Tracker', size = 28 }) {
  return (
    <div className="brand-wrap">
      <img className="brand-logo" src="/squirrel.svg" alt="" width={size} height={size} />
      <h1 className="brand">{title}</h1>
    </div>
  );
}
