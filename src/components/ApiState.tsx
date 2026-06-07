type ApiStateProps = {
  error: unknown;
};

export function ApiError({ error }: ApiStateProps) {
  return (
    <div className="api-error">
      {apiErrorMessage(error)}
    </div>
  );
}

export function InlineApiError({ error }: ApiStateProps) {
  return <div className="tip api-error-inline">{apiErrorMessage(error)}</div>;
}

export function apiErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message;
  return '后端没有实现或无法访问这个接口';
}
