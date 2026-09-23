import { Check, Mail, X } from "lucide-react";

type EmailApprovalCardProps = {
  to?: string;
  subject?: string;
  body?: string;
  status: "inProgress" | "executing" | "complete";
  onSubmit: (result: "APPROVED" | "REJECTED") => void;
};

export function EmailApprovalCard({
  to,
  subject,
  body,
  status,
  onSubmit,
}: EmailApprovalCardProps) {
  const canRespond = status === "executing";

  return (
    <section className="w-full max-w-md overflow-hidden rounded-lg border border-(--border) bg-(--card) text-(--card-foreground) shadow-sm">
      <header className="flex items-center gap-3 border-b border-(--border) px-4 py-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-(--accent)">
          <Mail className="size-4" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h3 className="text-sm font-semibold">Approve email</h3>
          <p className="text-xs text-(--muted-foreground)">
            Review this message before it is sent.
          </p>
        </div>
      </header>

      <dl className="grid grid-cols-[4.5rem_1fr] gap-x-3 gap-y-2 px-4 py-3 text-sm">
        <dt className="text-(--muted-foreground)">To</dt>
        <dd className="min-w-0 wrap-break-word font-medium">
          {to || "Waiting for recipient..."}
        </dd>
        <dt className="text-(--muted-foreground)">Subject</dt>
        <dd className="min-w-0 wrap-break-word font-medium">
          {subject || "Waiting for subject..."}
        </dd>
      </dl>

      <div className="mx-4 mb-4 rounded-md bg-(--muted) p-3 text-sm whitespace-pre-wrap wrap-break-word">
        {body || "Waiting for message..."}
      </div>

      <footer className="flex items-center justify-end gap-2 border-t border-(--border) px-4 py-3">
        {status === "complete" ? (
          <p className="text-xs text-(--muted-foreground)">Decision submitted</p>
        ) : (
          <>
            <button
              type="button"
              disabled={!canRespond}
              onClick={() => onSubmit("REJECTED")}
              className="inline-flex h-9 items-center gap-2 rounded-md border border-(--border) px-3 text-sm font-medium transition-colors hover:bg-(--muted) disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="size-4" aria-hidden="true" />
              Reject
            </button>
            <button
              type="button"
              disabled={!canRespond}
              onClick={() => onSubmit("APPROVED")}
              className="inline-flex h-9 items-center gap-2 rounded-md bg-(--primary) px-3 text-sm font-medium text-(--primary-foreground) transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Check className="size-4" aria-hidden="true" />
              Approve
            </button>
          </>
        )}
      </footer>
    </section>
  );
}
